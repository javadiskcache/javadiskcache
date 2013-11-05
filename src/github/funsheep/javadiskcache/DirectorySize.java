/*
	This file is part of the java diskcache library.
	Copyright (C) 2005-2013 funsheep, cgrote

	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
 */
package github.funsheep.javadiskcache;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class provides various methods to determine the size of a directory. It can constantly watch
 * a directory for changes or just scan on demand to get its size. This class is complete
 * Thread-safe and makes sure a directory is only registered once to a watch service.
 *
 * @author cgrote
 */
public final class DirectorySize implements Closeable
{
	private static final Logger LOGGER = Logger.getLogger();
	private static final ExecutorService threadPool = Executors.newCachedThreadPool();
	private static final WeakHashMap<Object, WeakReference<DirectorySize>> sizeWatcher = new WeakHashMap<Object, WeakReference<DirectorySize>>();

	private final ReentrantLock sizeLock = new ReentrantLock();
	private final ReentrantLock absoluteLock = new ReentrantLock();
	private final HashMap<Path, Long> fileSize = new HashMap<Path, Long>();
	private final HashMap<WatchKey, Path> keys = new HashMap<WatchKey, Path>();
	private final boolean recursive;
	private final Future< ? > future;
	private final Path _directory;

	private int closeCount = 0;
	private boolean threadReady = false;
	private WatchService watcher;
	private AtomicLong currentSize = new AtomicLong(0);

	private DirectorySize(final Path directory, final boolean recursive, final boolean useMultipleThreads)
	{
		this.recursive = recursive;
		this._directory = directory.normalize();

		try
		{
			watcher = FileSystems.getDefault().newWatchService();
		}
		catch (IOException e)
		{
			LOGGER.warn("IO Error while accessing " + directory, e);
			future = null;
			return;
		}
		updateDirectorySize(directory);

		future = threadPool.submit(new Runnable()
		{
			@Override
			public void run()
			{
				absoluteLock.lock();
				DirectorySize.this.setReady();

				while (true)
				{
					// wait for key to be signaled
					WatchKey key;
					try
					{
						key = watcher.poll();
						if (key == null)
						{
							absoluteLock.unlock();
							key = watcher.take();
							absoluteLock.lock();
						}
					}
					catch (final InterruptedException e)
					{

						return;
					}

					for (final WatchEvent< ? > event : key.pollEvents())
					{
						final Kind< ? > kind = event.kind();
						if (kind == StandardWatchEventKinds.OVERFLOW)
						{

							updateDirectorySize(directory);

						}
						else
						{
							Path dir;
							synchronized (keys)
							{
								dir = keys.get(key);
							}
							if (dir == null)
							{
								LOGGER.warn("Unknown Watchkey " + key);
								continue;
							}

							@SuppressWarnings("unchecked")
							final WatchEvent<Path> ev = (WatchEvent<Path>)event;
							final Path child = dir.resolve(ev.context());

							if (kind == StandardWatchEventKinds.ENTRY_CREATE)

							{
								if (recursive && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
								{
									if (useMultipleThreads)
										threadPool.execute(new Runnable()
										{

											@Override
											public void run()
											{
												updateDirectorySize(child);
											}
										});
									else
										updateDirectorySize(child);
								}
								else if (!Files.isSymbolicLink(child))
								{

									try
									{
										updateSize(child, Files.size(child));
									}
									catch (IOException e)
									{
										if (Files.notExists(child, LinkOption.NOFOLLOW_LINKS))
											removeSize(child);
									}

								}
							}
							else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
							{
								// nothing
							}
							else if (kind == StandardWatchEventKinds.ENTRY_DELETE)
							{
								if (!Files.isSymbolicLink(child))
								{
									removeSize(child);
								}
							}

							else if (kind == StandardWatchEventKinds.ENTRY_MODIFY)
							{

								try
								{
									if (!Files.isSymbolicLink(child))
									{
										updateSize(child, Files.size(child));
									}
								}
								catch (IOException e)
								{
									if (Files.notExists(child, LinkOption.NOFOLLOW_LINKS))
										removeSize(child);
								}
							}
						}

						final boolean valid = key.reset();
						if (!valid)
						{
							synchronized (keys)
							{
								keys.remove(key);
							}

							break;
						}

					}
				}
			}
		});

		while (!future.isDone() && !threadReady)
			Thread.yield();
	}

	private void setReady()
	{
		threadReady = true;
	}

	private void updateSize(Path file, long size)
	{
		sizeLock.lock();
		Long oldSize = fileSize.put(file.normalize(), Long.valueOf(size));
		if (oldSize != null)
			DirectorySize.this.currentSize.addAndGet(size - oldSize.longValue());
		sizeLock.unlock();
	}

	private void removeSize(Path file)
	{
		sizeLock.lock();
		Long oldSize = fileSize.remove(file.normalize());
		if (oldSize != null)
			DirectorySize.this.currentSize.addAndGet(-oldSize.longValue());
		sizeLock.unlock();
	}

	private void updateDirectorySize(final Path directory)
	{
		final HashSet<Path> files = new HashSet<Path>();

		try
		{
			Files.walkFileTree(directory, Collections.<FileVisitOption> emptySet(), recursive ? Integer.MAX_VALUE : 1,
				new SimpleFileVisitor<Path>()
				{
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr)
					{
						try
						{
							WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
								StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
							synchronized (keys)
							{
								keys.put(key, dir);
							}
						}
						catch (IOException e)
						{
							LOGGER.warn("IO error while accessing " + dir, e);
						}

						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc)
					{
						if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS))
							removeSize(file);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attr)
					{
						if (attr.isRegularFile())
						{
							updateSize(file, attr.size());
						}
						files.add(file);
						return FileVisitResult.CONTINUE;
					}
				});
		}
		catch (IOException e)
		{
			LOGGER.warn("IO error while scanning " + directory, e);
			return;
		}

		sizeLock.lock();
		Iterator<Entry<Path, Long>> it = fileSize.entrySet().iterator();
		while (it.hasNext())
		{
			Entry<Path, Long> e = it.next();
			if (!files.contains(e.getKey()))
			{
				DirectorySize.this.currentSize.addAndGet(-e.getValue().longValue());
				it.remove();
			}

		}
		sizeLock.unlock();
	}

	/**
	 * @return a directory watcher that observe the directory for changes. Use the close() method to
	 *         stop watching the directory.
	 */
	public static DirectorySize getWatcher(Path directory)
	{
		return DirectorySize.getWatcher(directory, false, false);
	}

	/**
	 * @return a directory watcher that observe the directory for changes. Use the close() method to
	 *         stop watching the directory.
	 */
	public static DirectorySize getWatcher(Path directory, boolean recursive, boolean useMultipleWatchThreads)
	{
		DirectorySize watcher;
		synchronized (sizeWatcher)
		{
			final WeakReference<DirectorySize> ref = sizeWatcher.get(new DirectoryKey(directory, recursive));

			if (ref == null || (watcher = ref.get()) == null)
			{
				watcher = new DirectorySize(directory, recursive, useMultipleWatchThreads);
				sizeWatcher.put(watcher, new WeakReference<DirectorySize>(watcher));
			}
			watcher.closeCount++;
		}
		return watcher;
	}

	/**
	 * Determines the current size of the watched directory in bytes. </p> Note: Use the
	 * getActualSize() method to make sure all queued changes are processed.
	 *
	 * @return the current size of the watched directory.
	 */
	public long getSize()
	{
		return currentSize.longValue();
	}

	/**
	 * Determines the current size of the watched directory, but unlike the getSize() method this
	 * will make sure all queued changes are processed before this method returns.
	 *
	 * @return the current size of the watched directory in bytes, but unlike the getSize() method
	 *         this will make sure all queued changes are processed before this method returns.
	 */
	public long getActualSize()
	{
		absoluteLock.lock();
		long size = currentSize.longValue();
		absoluteLock.unlock();
		return size;
	}

	/**
	 * Scans the directory and return its size in bytes.
	 *
	 * @return the size of the directory in bytes.
	 */
	public static long directorySize(final Path directory)
	{
		return DirectorySize.directorySize(directory, false);
	}

	/**
	 * Scans the directory and return its size in bytes. If recursive is true, than all
	 * sub-directories are included.
	 *
	 * @return the size of the directory in bytes.
	 */
	public static long directorySize(final Path directory, boolean recursive)
	{
		final long[] size = new long[1];
		try
		{
			Files.walkFileTree(directory, Collections.<FileVisitOption> emptySet(), recursive ? Integer.MAX_VALUE : 1,
				new SimpleFileVisitor<Path>()
				{

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attr)
					{
						if (attr.isRegularFile())
						{
							size[0] += attr.size();
						}
						return FileVisitResult.CONTINUE;
					}
				});
		}
		catch (IOException e)
		{
			LOGGER.warn("IO error while scanning " + directory, e);
			return -1;
		}

		return size[0];
	}

	/**
	 * Scans the directory and return its size in bytes. Unlike the directorySize() methods, this
	 * makes sure all changes to the directory during the scan are represented in the size.
	 *
	 * @return the size of the directory in bytes.
	 */
	public static long directoryActualSize(Path directory)
	{
		return directoryActualSize(directory, false);
	}

	/**
	 * Scans the directory and return its size in bytes. Unlike the directorySize() methods, this
	 * makes sure all changes to the directory during the scan are represented in the size. If
	 * recursive is true, than all sub-directories are included.
	 *
	 * @return the size of the directory in bytes.
	 */
	public static long directoryActualSize(Path directory, boolean recursive)
	{
		final DirectorySize watcher = DirectorySize.getWatcher(directory);
		long size = watcher.getActualSize();
		Tools.close(watcher);
		return size;
	}

	/**
	 * Closes this stream and releases any system resources associated with it, if the directory is
	 * watched by no other reference. You should call this method only once for each execution of
	 * the DirectorySizeWatcher.getWatcher(..) method.
	 */
	@Override
	public void close() throws IOException
	{
		synchronized (sizeWatcher)
		{
			if (--closeCount > 0)
				return;
			future.cancel(true);
			keys.clear();
			fileSize.clear();
			watcher.close();
		}
	}

	@Override
	public int hashCode()
	{
		return this._directory.hashCode();
	}

	public boolean equals(DirectoryKey o)
	{
		return o._directoryKey.equals(this._directory) && o.recursiveKey == this.recursive;
	}

	public boolean equals(DirectorySize o)
	{
		return o._directory.equals(this._directory) && o.recursive == this.recursive;
	}

	private static class DirectoryKey
	{
		final Path _directoryKey;
		final boolean recursiveKey;

		DirectoryKey(final Path directory, final boolean recursive)
		{
			this._directoryKey = directory.normalize();
			this.recursiveKey = recursive;
		}

		@SuppressWarnings("unused")
		public boolean equals(DirectoryKey obj)
		{
			return obj.recursiveKey == this.recursiveKey && obj._directoryKey.equals(this._directoryKey);
		}

		@SuppressWarnings("unused")
		public boolean equals(DirectorySize obj)
		{
			return obj.recursive == this.recursiveKey && obj._directory.equals(this._directoryKey);
		}

		@Override
		public int hashCode()
		{
			return this._directoryKey.hashCode();
		}
	}

}
