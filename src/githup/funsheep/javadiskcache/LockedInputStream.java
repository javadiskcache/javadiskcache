/*
	This file is part of the java diskcache library.
	Copyright (C) 2005-2013 funsheep, cgrote
	
	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the 
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
*/
package githup.funsheep.javadiskcache;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Release Lock on close and optionally delete files. Useful if lock-file != stream-file
 */
class LockedInputStream extends FilterInputStream
{
	private final FileLock lock;
	private final Path[] files;

	/**
	 * @param lock The lock to be released on close.
	 */
	LockedInputStream(InputStream in, FileLock lock)
	{
		super(in);
		this.lock = lock;
		this.files = null;
	}

	/**
	 * @param lock The lock to be released on close.
	 * @param files Files to be deleted when this stream is closed.
	 */
	LockedInputStream(InputStream in, FileLock lock, Path... files)
	{
		super(in);
		this.lock = lock;
		this.files = files;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException
	{
		super.close();
		if(this.lock.channel().isOpen())
			this.lock.release();
		this.lock.channel().close();
		if (files != null)
			for (Path file : files)
				Files.deleteIfExists(file);
	}
}
