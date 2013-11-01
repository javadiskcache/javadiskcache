/*
	This file is part of the d3fact common library.
	Copyright (C) 2005-2012 d3fact Project Team

	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
*/
package funsheep.githup.javadiskcache;

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
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class provides various methods to determine the content of a directory. It can constantly
 * watch a directory for changes or just scan on demand for to get it's content. This class is
 * complete Thread-safe and makes sure a directory is only registered once to a watch service.
 *
 * @author cgrote
 */
final public class DirectoryContent implements Closeable
{
	private static final Logger LOGGER = Logger.getLogger();
	private static final ExecutorService threadPool = Executors.newCachedThreadPool();
	private static final WeakHashMap<Object, WeakReference<DirectoryContent>> dirWatcher = new WeakHashMap<Object, WeakReference<DirectoryContent>>();

	private final ReentrantLock contentLock = new ReentrantLock();
	private final ReentrantLock absoluteLock = new ReentrantLock();
	private final HashSet<Path> dirContent = new HashSet<Path>();
	private final HashMap<WatchKey, Path> keys = new HashMap<WatchKey, Path>();
	private final boolean recursive;
	private final Future< ? > future;
	private final Path _directory;

	private int closeCount = 0;
	private boolean threadReady = false;
	private WatchService watcher;

	private DirectoryContent(final Path directory, final boolean recursive, final boolean useMultipleThreads)
	{
		this.recursive = recursive;
		this._directory = directory.normalize();

		try
		{
			watcher = FileSystems.getDefault().newWatchService();
		} catch (IOException e)
		{
			LOGGER.warn("IO Error while accessing " + directory, e);
			future = null;
			return;
		}
		updateDirectoryContent(directory);

		future = threadPool.submit(new Runnable()
			{
				@Override
				public void run()
				{
					absoluteLock.lock();
					DirectoryContent.this.setReady();

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
						} catch (final InterruptedException e)
						{

							return;
						}

						for (final WatchEvent< ? > event : key.pollEvents())
						{
							final Kind< ? > kind = event.kind();
							if (kind == StandardWatchEventKinds.OVERFLOW)
							{

								updateDirectoryContent(directory);

							} else
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
														updateDirectoryContent(child);
													}
												});
										else
											updateDirectoryContent(child);
									} else if (!Files.isSymbolicLink(child))
									{
										updateContent(child);
									}
								} else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
								{
									// nothing
								} else if (kind == StandardWatchEventKinds.ENTRY_DELETE)
								{
									if (!Files.isSymbolicLink(child))
									{
										removeContent(child);
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

	private void updateContent(Path file)
	{
		contentLock.lock();
		dirContent.add(file.normalize());
		contentLock.unlock();
	}

	private void removeContent(Path file)
	{
		contentLock.lock();
		dirContent.remove(file.normalize());
		contentLock.unlock();
	}

	private void updateDirectoryContent(final Path directory)
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
									StandardWatchEventKinds.ENTRY_DELETE);
								synchronized (keys)
								{
									keys.put(key, dir);
								}
							} catch (IOException e)
							{
								LOGGER.warn("IO error while accessing " + dir, e);
							}

							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFileFailed(Path file, IOException exc)
						{
							if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS))
								removeContent(file);
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attr)
						{
							if (attr.isRegularFile())
							{
								updateContent(file);
							}
							files.add(file);
							return FileVisitResult.CONTINUE;
						}
					});
		} catch (IOException e)
		{
			LOGGER.warn("IO error while scanning " + directory, e);
			return;
		}

		contentLock.lock();
		dirContent.retainAll(files);
		contentLock.unlock();
	}

	/**
	 * @return a directory watcher that observe the directory for changes. Use the close() method to
	 *         stop watching the directory.
	 */
	public static DirectoryContent getWatcher(Path directory)
	{
		return DirectoryContent.getWatcher(directory, false, false);
	}

	/**
	 * @return a directory watcher that observe the directory for changes. Use the close() method to
	 *         stop watching the directory.
	 */
	public static DirectoryContent getWatcher(Path directory, boolean recursive, boolean useMultipleWatchThreads)
	{
		DirectoryContent watcher;
		synchronized (dirWatcher)
		{
			final WeakReference<DirectoryContent> ref = dirWatcher.get(new DirectoryKey(directory, recursive));

			if (ref == null || (watcher = ref.get()) == null)
			{
				watcher = new DirectoryContent(directory, recursive, useMultipleWatchThreads);
				dirWatcher.put(watcher, new WeakReference<DirectoryContent>(watcher));
			}
			watcher.closeCount++;
		}
		return watcher;
	}

	/**
	 * Determines the current content of the watched directory in bytes. </p> Note: Use the
	 * getActualContent() method to make sure all queued changes are processed.
	 *
	 * @return the current size of the watched directory.
	 */
	public ArrayDeque<Path> getContent()
	{
		return new ArrayDeque<Path>(dirContent);
	}

	/**
	 * Determines the current content of the watched directory, but unlike the getContent() method
	 * this will make sure all queued changes are processed before this method returns.
	 *
	 * @return the current content of the watched content as an ArrayDeque, but unlike the
	 *         getContent() method this will make sure all queued changes are processed before this
	 *         method returns.
	 */
	public ArrayDeque<Path> getActualContent()
	{
		absoluteLock.lock();
		ArrayDeque<Path> content = new ArrayDeque<Path>(dirContent);
		absoluteLock.unlock();
		return content;
	}

	/**
	 * Scans the directory and return its content as an ArrayDeque.
	 *
	 * @return the content of the directory as an ArrayDeque..
	 */
	public static ArrayDeque<Path> directoryContent(final Path directory)
	{
		return DirectoryContent.directoryContent(directory, false);
	}

	/**
	 * Scans the directory and return its content as an ArrayDeque. If recursive is true, than all
	 * sub-directories are included.
	 *
	 * @return the content of the directory as an ArrayDeque.
	 */
	public static ArrayDeque<Path> directoryContent(final Path directory, boolean recursive)
	{
		final ArrayDeque<Path> content = new ArrayDeque<Path>();
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
								attr.lastAccessTime();
								content.add(file);
							}
							return FileVisitResult.CONTINUE;
						}
					});
		} catch (IOException e)
		{
			LOGGER.warn("IO error while scanning " + directory, e);
		}

		return content;
	}

	/**
	 * Scans the directory and return its content as an ArrayDeque. If recursive is true, than all
	 * sub-directories are included.
	 *
	 * @return the content of the directory as an ArrayDeque.
	 */
	public static TreeMap<FileTime, Path> directoryContentLastAccess(final Path directory, boolean recursive)
	{
		final TreeMap<FileTime, Path> content = new TreeMap<FileTime, Path>(new Comparator<FileTime>()
			{

				@Override
				public int compare(FileTime o1, FileTime o2)
				{
					final long l = o1.toMillis() - o2.toMillis();
					return l > 0f ? 1 : l < 0f ? -1 : 0;	//signum
				}
			});

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
								content.put(attr.lastAccessTime(), file);
							}
							return FileVisitResult.CONTINUE;
						}
					});
		} catch (IOException e)
		{
			LOGGER.warn("IO error while scanning " + directory, e);
		}

		return content;
	}

	/**
	 * Scans the directory and return its content as an ArrayDeque. Unlike the directorySize()
	 * methods, this makes sure all changes to the directory during the scan are represented in the
	 * size.
	 *
	 * @return the content of the directory as an ArrayDeque.
	 */
	public static ArrayDeque<Path> directoryActualContent(Path directory)
	{
		return directoryActualContent(directory, false);
	}

	/**
	 * Scans the directory and return its content as an ArrayDeque. Unlike the directoryContent()
	 * methods, this makes sure all changes to the directory during the scan are represented in the
	 * size. If recursive is true, than all sub-directories are included.
	 *
	 * @return the content of the directory as an ArrayDeque.
	 */
	public static ArrayDeque<Path> directoryActualContent(Path directory, boolean recursive)
	{
		final DirectoryContent watcher = DirectoryContent.getWatcher(directory);
		ArrayDeque<Path> content = watcher.getActualContent();
		Tools.close(watcher);
		return content;
	}

	/**
	 * Closes this stream and releases any system resources associated with it, if the directory is
	 * watched by no other reference. You should call this method only once for each execution of
	 * the DirectorySizeWatcher.getWatcher(..) method.
	 */
	@Override
	public void close() throws IOException
	{
		synchronized (dirWatcher)
		{
			if (--closeCount > 0)
				return;
			future.cancel(true);
			keys.clear();
			dirContent.clear();
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

	public boolean equals(DirectoryContent o)
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
		public boolean equals(DirectoryContent obj)
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
