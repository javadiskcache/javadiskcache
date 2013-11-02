/*
	This file is part of the java diskcache library.
	Copyright (C) 2005-2013 funsheep, cgrote
	
	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the 
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
*/
package githup.funsheep.javadiskcache;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Transparent fileCache for {@link InputStream} implementation with simple wrapping mechanism.
 * Features:
 * Detects multiple streams for a given id and automatically closes additional streams.
 * Checks free-space on drive prior to storing a file.
 * Multi-Thread Support and Multi-JVM support.
 * Global cache-size limit with LRU-Schema.
 *
 * @author cgrote
 */
public class FileCache
{
	public static long CacheSizeLimit = 1 * 1024 * 1024 * 1024; // GB
	private static final long NOT_AVAILABLE = -1;
	private static final String UniqueID = Tools.toBase64String(UUID.randomUUID().toString());
	private static final Logger LOGGER = Logger.getLogger();
	private static final Path cacheDir = Paths.get(userTempDir(), "d3fact_cache");
	private static final Path boundlessDir = Paths.get(cacheDir.toString(), "boundless");
	private static final Path modifiedDir = Paths.get(cacheDir.toString(), "lastModified");
	private static long currentSize = 0; // in Bytes

	private static final DirectoryContent dirWatcher;

	static
	{
		dirWatcher = DirectoryContent.getWatcher(cacheDir);
		try
		{
			Files.createDirectories(cacheDir);
			Files.createDirectories(boundlessDir);
			Files.createDirectories(modifiedDir);
			Files.createDirectories(modifiedDir.resolve("boundless"));
		}
		catch (IOException e)
		{
			LOGGER.warn("Could not create cache directory " + getCacheDir());
		}
	}

	public static synchronized ReadableByteChannel getCachedByteChannel(String uid, InputStream input, long size, long lastModified)
		throws IOException
	{
		return Channels.newChannel(getCachedInputStream(uid, input, size, lastModified));
	}

	/**
	 * If input is <i>null</i> it will return the local cache file with a matching id, size and
	 * lastModified time-stamp. Use <i>-1</i> for size and lastModified to get the latest version of
	 * the file. return null if no matching complete file can be found.
	 *
	 * @param id: unique name of the resource, should not change with different versions of the same
	 *            file.
	 */
	public static synchronized InputStream getCachedInputStream(String id, InputStream input, long size, long lastModified)
		throws IOException
	{
		if (input == null)
		{
			// try getting latest version from cache

			final Path file;
			if (size == NOT_AVAILABLE && lastModified == NOT_AVAILABLE)
				file = getLatestVersionCacheFile(id);
			else
				file = getCacheFile(id, size, lastModified);

			if (file == null)
				return input;

			if (Files.exists(file))
			{
				if (isFileComplete(file) && Files.isReadable(file))
				{
					final InputStream bin = new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ));
					InputStream stream = read(file, bin);
					if (stream != null)
					{
						if (size == NOT_AVAILABLE && lastModified == NOT_AVAILABLE)
							LOGGER.warn("Could not receive the resource " + id + " from the server. Using the latest cached version.");
						else
							LOGGER.info("Read from cache " + id);
						return stream;
					}

					// could not create ReadLock -> no backup Stream
					Tools.close(bin); // cleanup

				}
			}
			return null;
		}

		final Path file = getCacheFile(id, size, lastModified);
		final String uid = getUID(id, size, lastModified);

		if (Files.exists(file)) // space already reserved
		{
			// already downloading?
			InputStream stream = CachedInputStream.getStreamFromLoader(uid);
			if (stream != null)
			{
				Tools.close(input); // another open stream already exists.
				LOGGER.info("Read from cache stream " + id);
				final InputStream in = read(file, stream);
				if (in != null)
				{
					return in;
				}

				return stream;
			}

			if (isFileComplete(file))
			{
				if (Files.isReadable(file))
				{
					LOGGER.info("Read from cache " + id);

					final InputStream bin = new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ));
					stream = read(file, bin);
					if (stream != null)
					{
						Tools.close(input);
					}
					else
					{
						Tools.close(bin); // cleanup
						// could not create ReadLock -> backupStream
						try
						{
							stream = new HybridInputStream(input, Files.newByteChannel(file, StandardOpenOption.READ));
						}
						catch (IOException e)
						{
							return input;
						}
					}

					return stream;
				}
			}

			else if (Files.isWritable(file)) // try resume
			{
				LOGGER.info("Resume data in cache " + id);
				InputStream in = write(file, input);
				if (in != null)
					return new CachedInputStream(in, Files.newByteChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE), uid,
						true);
			}

			if (Files.isReadable(file))
			{
				LOGGER.info("Hybrid data read cache " + id);
				stream = new HybridInputStream(input, Files.newByteChannel(file, StandardOpenOption.READ));
				InputStream in = read(file, stream);

				if (in != null)
					return in;

				return stream;
			}
		}
		// not Cached
		if (spaceAvailable(file, size))
		{
			LOGGER.info("Create data in cache " + id);
			final InputStream in = write(file, input);
			if (in != null && Files.isWritable(file))
			{
				SeekableByteChannel chan = Files.newByteChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
				CachedInputStream cachedIn = new CachedInputStream(in, chan, uid);
				final InputStream cin = read(file, cachedIn);
				if (cin != null)
					return cin;

				Tools.close(chan);
			}
		}

		LOGGER.info("No data cached " + id);
		return input; // everything else failed, so just load from stream directly without caching
	}

	private static long currentSize()
	{
		currentSize = 0;
		final ArrayDeque<Path> content = dirWatcher.getActualContent();
		for (Path p : content)
		{
			if (!Files.isDirectory(p))
			{
				currentSize += getSize(p);
			}
		}
		return currentSize;
	}

	/**
	 * check if space is Available, if not try to free it up and create file
	 */
	private static boolean spaceAvailable(Path file, long size)
	{
		try
		{
			Files.createDirectories(file.getParent());
			Files.createFile(file);
		}
		catch (IOException e)
		{
			return false;
		}

		long boundlessSize = boundless();

		if (currentSize + boundlessSize > CacheSizeLimit)
		{
			TreeMap<FileTime, Path> files = getLastModifiedFiles();

			while (currentSize + boundlessSize > CacheSizeLimit && !files.isEmpty())
			{
				Path f = files.pollFirstEntry().getValue();
				Path p = f.getParent();
				f = f.getFileName();
				while (!p.endsWith(Paths.get("lastModified")))
				{
					f = p.getFileName().resolve(f);
					p = p.getParent();
				}
				f = p.getParent().resolve(f);

				delete(f);
				if (currentSize + boundlessSize < CacheSizeLimit)
					currentSize();
			}
			try
			{
				if (currentSize + boundlessSize > CacheSizeLimit
					|| (size != -1 && Files.getFileStore(cacheDir).getUnallocatedSpace() < size))
				{
					Files.delete(file);
					return false;
				}
			}
			catch (IOException e)
			{
				try
				{
					Files.delete(file);
				}
				catch (IOException ioe)
				{
					// nothing
				}
				return false;
			}
		}

		return true;
	}

	private static long boundless()
	{
		return DirectorySize.directorySize(boundlessDir, true);
	}

	private static TreeMap<FileTime, Path> getLastModifiedFiles()
	{
		return DirectoryContent.directoryContentLastAccess(modifiedDir, true);
	}

	private static InputStream write(Path file, InputStream in)
	{
		final Path lockFile = writeLock(file);
		FileLock wlock = createLock(lockFile);
		if (wlock == null)
			return null;

		accessUpdate(file);
		return new LockedInputStream(in, wlock, lockFile);
	}

	private static InputStream read(Path file, InputStream in)
	{
		FileLock rlock = makeReadlock(file);
		if (rlock == null) // wait a bit, than return
		{
			try
			{
				Thread.sleep(50);
			}
			catch (InterruptedException e)
			{
				// nothing
			}
			return null;
		}
		accessUpdate(file);
		return new LockedInputStream(in, rlock, readLock(file));
	}

	/**
	 * Check file locks, perform cleanup if necessary.
	 *
	 * @return true if lock is valid, false otherwise.
	 */
	private static boolean checkLock(final Path lockFile)
	{
		if (Files.isDirectory(lockFile))
		{
			final boolean[] lock = new boolean[1];
			lock[0] = false;

			try
			{
				Files.walkFileTree(lockFile, Collections.<FileVisitOption> emptySet(), 1, new SimpleFileVisitor<Path>()
				{
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attr)
					{
						if (attr.isRegularFile())
						{
							if (checkLock(lockFile))
							{
								lock[0] = true;
								return FileVisitResult.TERMINATE;
							}
						}
						return FileVisitResult.CONTINUE;
					}
				});

			}
			catch (IOException e)
			{
				return true;
			}

			return lock[0];
		}

		FileChannel channel = null;
		try
		{
			channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
			FileLock lock = channel.tryLock();
			if (lock != null)
			{
				Files.deleteIfExists(lockFile);
				lock.release();
				return false;
			}
			return true;
		}
		catch (IOException e)
		{
			if (e instanceof NoSuchFileException)
				return false;
			return true;
		}
		finally
		{
			Tools.close(channel);
		}
	}

	/**
	 * try to delete a file. return true if no other process is reading the file and it could be
	 * deleted.
	 */
	private static boolean delete(Path file)
	{
		if (checkLock(writeLock(file)) || checkLock(readLocks(file)))
		{
			return false;
		}

		Path lockFile = deleteLock(file);
		FileLock deleteLock = createLock(lockFile);

		if (deleteLock == null)
			return false;

		if (checkLock(writeLock(file)) || checkLock(readLocks(file)))
		{
			removeLock(lockFile, deleteLock);
			return false;
		}

		try
		{
			currentSize -= getSize(file);
			Files.deleteIfExists(file);
			Files.deleteIfExists(readLocks(file));
			Files.deleteIfExists(lastAccessedFile(file));
		}
		catch (IOException e)
		{
			// nothing
		}
		finally
		{
			removeLock(lockFile, deleteLock);
		}

		return true;

	}

	private static FileLock createLock(Path lockFile)
	{

		try
		{
			Files.createDirectories(lockFile.getParent());
		}
		catch (IOException e)
		{
			return null;
		}

		try
		{
			Files.createFile(lockFile);
		}
		catch (IOException e)
		{
			if (!(e instanceof FileAlreadyExistsException))
				return null;
		}

		FileChannel channel = null;
		try
		{
			channel = FileChannel.open(lockFile, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
			final FileLock lock = channel.tryLock();
			if (lock == null)
				Tools.close(channel);
			return lock;

		}
		catch (IOException e)
		{
			Tools.close(channel);
			return null;
		}
	}

	private static void removeLock(Path lockFile, FileLock lock)
	{

		try
		{
			lock.release();
		}
		catch (IOException e)
		{
			// nothing
		}
		finally
		{
			Tools.close(lock.channel());
		}

	}

	private static FileLock makeReadlock(Path file)
	{
		final Path rLock = readLock(file);
		final Path dLock = deleteLock(file);

		if (checkLock(dLock))
		{
			return null;
		}

		else if (Files.exists(file))
		{
			FileLock lock = createLock(rLock);
			if (lock != null)
			{
				if (checkLock(dLock))
				{
					removeLock(rLock, lock);
					return null;
				}
			}

			return lock;
		}

		return null;
	}

	static Path getCacheDir()
	{
		return Paths.get(userTempDir(), "d3fact_cache");
	}

	static Path getCacheFile(String id, long size, long lastModified)
	{
		final String filename = getUID(id, size, lastModified);
		if (size == -1)
			return Paths.get(userTempDir(), "d3fact_cache", "boundless", filename);
		return Paths.get(userTempDir(), "d3fact_cache", filename);
	}

	static Path getLatestVersionCacheFile(String id)
	{
		Path filename = null;
		long lastModified = 0;

		final ArrayDeque<Path> content = dirWatcher.getActualContent();
		for (Path path : content)
		{
			if (getID(path).equals(id) && getLastModified(path) > lastModified)
			{
				filename = path;
				lastModified = getLastModified(path);
			}
		}
		if (filename == null)
			filename = Paths.get(userTempDir(), "d3fact_cache", "boundless", getUID(id, NOT_AVAILABLE, NOT_AVAILABLE));

		if (Files.notExists(filename))
			return null;
		return filename;
	}

	private static String getUID(String id, long size, long lastModified)
	{
		final byte[] uidbyte = id.getBytes();
		final byte[] data = new byte[uidbyte.length + 16];

		System.arraycopy(uidbyte, 0, data, 0, uidbyte.length);
		System.arraycopy(Tools.longToByte(size, lastModified), 0, data, uidbyte.length, 16);

		return Tools.toBase64String(data);
	}

	static long getSize(Path file)
	{
		final byte[] data = Tools.fromBase64String(file.getFileName().toString());
		final byte[] size = Arrays.copyOfRange(data, data.length - 16, data.length - 8);

		return ByteBuffer.wrap(size).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get();
	}

	static long getLastModified(Path file)
	{
		final byte[] data = Tools.fromBase64String(file.getFileName().toString());
		final byte[] lastModified = Arrays.copyOfRange(data, data.length - 8, data.length);

		return ByteBuffer.wrap(lastModified).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get();
	}

	static String getID(Path file)
	{
		final byte[] data = Tools.fromBase64String(file.getFileName().toString());
		final byte[] id = Arrays.copyOfRange(data, 0, data.length - 16);
		return new String(id);
	}

	private static void accessUpdate(Path file)
	{
		final Path lastAccessed = lastAccessedFile(file);

		try
		{
			if (Files.notExists(lastAccessed))
				Files.createFile(lastAccessed);
		}
		catch (IOException e)
		{
			if (!(e instanceof FileAlreadyExistsException))
				return;
		}

		try
		{
			Files.setLastModifiedTime(lastAccessed, FileTime.fromMillis(System.currentTimeMillis()));
		}
		catch (IOException e)
		{
			// nothing
		}
	}

	private static Path lastAccessedFile(Path file)
	{
		if (getSize(file) == -1)
			return file.getParent().getParent()
				.resolveSibling("lastModified" + File.separatorChar + "boundless" + File.separatorChar + file.getFileName() + ".modified");
		return file.resolveSibling("lastModified" + File.separatorChar + file.getFileName() + ".modified");
	}

	private static Path readLock(Path file)
	{
		return file.resolveSibling("rlock" + File.separatorChar + file.getFileName() + UniqueID + ".rlock");
	}

	private static Path readLocks(Path file)
	{
		return file.resolveSibling("rlock" + File.separatorChar + file.getFileName());
	}

	private static Path deleteLock(Path file)
	{
		return file.resolveSibling("dlock" + File.separatorChar + file.getFileName() + ".remove");
	}

	private static Path writeLock(Path file)
	{
		return file.resolveSibling("wlock" + File.separatorChar + file.getFileName() + ".wlock");
	}

	static boolean isFileComplete(Path file)
	{
		// check if active wlock is set
		if (checkLock(writeLock(file)))
		{
			return false;
		}

		// if not check size
		long size = getSize(file);
		if (size != -1)
			try
			{
				if (Files.size(file) == size)
					return true;

				return false;
			}
			catch (IOException e)
			{
				return false; // could not get size, assume incomplete
			}

		return true; // unknown file size, assume it's complete.
	}
	
	private static String USER_TEMP_DIR;
	public static final String userTempDir()
	{
		if (USER_TEMP_DIR == null)
		{
			StringBuilder sb = new StringBuilder(30);
			String tmpdir = System.getProperty("java.io.tmpdir");
			if (tmpdir.charAt(tmpdir.length()-1) != File.separatorChar)
				tmpdir += File.separatorChar;
			sb.append(tmpdir);
			sb.append(Tools.getHex(System.getProperty("user.name").getBytes()));
			sb.append(File.separatorChar);
			USER_TEMP_DIR = sb.toString();

			File path = new File(USER_TEMP_DIR);
			if (!path.exists() && !path.mkdirs())
			{
				USER_TEMP_DIR = tmpdir;
				LOGGER.warn("Could not create directory structure for path: " + path.toString() + " !");
			}
			LOGGER.info("Using temporary directory: " + USER_TEMP_DIR);
		}
		return USER_TEMP_DIR;
	}

}