/*
	This file is part of the java diskcache library.
	Copyright (C) 2005-2013 funsheep, cgrote
	
	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the 
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
*/
package githup.funsheep.javadiskcache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * 
 * @author work
 *
 */
public class CachedInputStream extends InputStream
{
	private final CacheStreamLoader cachedIn;
	private ByteBuffer buffer = ByteBuffer.allocate(8192);
	private long inputPosition = 0;
	private boolean closed = false;

	public CachedInputStream(final InputStream in, SeekableByteChannel out, String uid)
	{
		this(in, out, uid, false);
	}

	public CachedInputStream(final InputStream in, SeekableByteChannel out, String uid, boolean autoload)
	{
		CacheStreamLoader cin;
		try
		{
			cin = CacheStreamLoader.getStream(in, out, uid, out.size(), autoload);
		} catch (IOException e)
		{
			cin = CacheStreamLoader.getStream(in, out, uid, 0, autoload);
		}

		this.cachedIn = cin;
		buffer.limit(0);
	}

	private CachedInputStream(final CacheStreamLoader cin)
	{
		this.cachedIn = cin;
		buffer.limit(0);
	}

	@Override
	public synchronized int read() throws IOException
	{
		if (!buffer.hasRemaining()) // fill buffer if empty
		{
			buffer.clear();
			final int read;
			if ((read = cachedIn.read(inputPosition, buffer)) > 0)
				inputPosition += read;

			if (read == -1)
				return read;
		}

		if (buffer.hasRemaining())
			return buffer.get() & 0xff;


		return -1;
	}

	@Override
	public synchronized int read(byte b[]) throws IOException
	{
		return this.read(b, 0, b.length);
	}

	@Override
	public synchronized int read(byte b[], int off, int len) throws IOException
	{

		if (!buffer.hasRemaining()) //fill buffer if empty
		{
			buffer.clear();
			final int read;
			if ((read = cachedIn.read(inputPosition, buffer)) > 0)
				inputPosition += read;

			if (read == -1){
				buffer.limit(0);
				return read;
			}
		}

		if (buffer.hasRemaining()) // copy to array
		{
			final int read = Math.min(len, buffer.remaining());
			buffer.get(b, off, read);
			return read;
		}

		return -1;

	}

	@Override
	public int available() throws IOException
	{
		return buffer.remaining() + cachedIn.available(inputPosition);
	}

	@Override
	public void close() throws IOException
	{
		if (closed)
			return;

		closed = true;
		buffer = null;
		cachedIn.close();
	}

	@Override
	public boolean markSupported()
	{
		return false;
	}

	@Override
	public synchronized void mark(int readlimit)
	{
		// not supported
	}

	@Override
	public synchronized void reset()
	{
		// not supported
	}

	@Override
	public synchronized long skip(long n) throws IOException
	{
		int remaining = (int)Math.min(buffer.remaining(), n);

		n -= remaining;
		if (remaining > 0)
			buffer.position(buffer.position() + remaining);
		if (n > 0)
		{
			long skip = cachedIn.skip(inputPosition, n);
			inputPosition += skip;
			return remaining + skip;
		}

		return remaining;
	}

	public static CachedInputStream getStreamFromLoader(String uid){
		CacheStreamLoader csl = CacheStreamLoader.getStream(uid);
		if(csl == null)
			return null;
		return new CachedInputStream(csl);
	}
}
