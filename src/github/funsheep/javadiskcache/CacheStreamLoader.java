/*
	This file is part of the java diskcache library.
	Copyright (C) 2005-2013 funsheep, cgrote
	
	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the 
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
*/
package github.funsheep.javadiskcache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.HashMap;

/**
 * 
 * @author cgrote
 */
class CacheStreamLoader
{
	private static final Logger LOGGER = Logger.getLogger();
	private static final HashMap<String, CacheStreamLoader> cacheLoader = new HashMap<String, CacheStreamLoader>();

	private final SeekableByteChannel chan;
	private final String uid;
	private ByteBuffer buffer = ByteBuffer.allocate(8192);
	private long position = 0;
	private long streamCount = 0;
	private final InputStream in;
	private boolean closed = false;

	private CacheStreamLoader(final InputStream in, SeekableByteChannel out, String uid, long startpos, boolean autoload)
	{
		this.position = startpos;
		this.in = in;
		this.chan = out;

		try
		{
			this.chan.position(this.position);
			long skip = this.in.skip(this.position);
			if (skip < this.position)
			{
				try
				{
					this.chan.position(skip);
				} catch (IOException ioe)
				{
					throw new RuntimeException(ioe);
				}
			}
		} catch (IOException e)
		{
			this.position = 0;
			try
			{
				this.chan.position(0);
			} catch (IOException ioe)
			{
				LOGGER.warn("", ioe);
			}
		}
		this.uid = uid;

		if (autoload)
			new Thread()
				{

					@Override
					public void run()
					{
						try
						{
							synchronized (cacheLoader)
							{
								streamCount++;
							}
							CacheStreamLoader.this.skip(Long.MAX_VALUE);
							close();

						} catch (IOException e)
						{
							LOGGER.warn("Could not read from Stream + " + in, e);
						}

					}
				}.start();
	}

	private synchronized int read(byte b[], int off, int len) throws IOException
	{
		if (closed)
			return -1;

		final int read = in.read(b, off, len);

		if (read > 0)
		{
			position += read;
			int pos = 0;
			do
			{
				if (buffer.hasRemaining())
				{
					int toCopy = Math.min(read - pos, buffer.remaining());
					buffer.put(b, pos + off, toCopy);
					pos += toCopy;
				} else
				{
					buffer.flip();
					while (buffer.hasRemaining())
						chan.write(buffer);
					buffer.clear();
				}
			} while (pos < read);
		} else
			this.closeInput();

		return read;
	}

	private synchronized void closeInput() throws IOException
	{
		if (closed)
			return;

		if (buffer.position() > 0) // flush content
		{
			final int limit = buffer.limit();
			final int bpos = buffer.position();
			buffer.flip();
			while (buffer.hasRemaining())
				chan.write(buffer);
			buffer.limit(limit);
			buffer.position(bpos);
		}

		in.close();
		closed = true;
	}

	synchronized void close() throws IOException
	{
		synchronized (cacheLoader)
		{
			if (--streamCount == 0)
			{
				closeInput();
				chan.close();
				buffer = null;
				cacheLoader.remove(uid);
			}
		}
	}

	synchronized int read(long inputPosition, ByteBuffer buf) throws IOException
	{
		int deltaP = (int)(position - inputPosition);

		int read = 0;

		if (deltaP <= 0) // read ahead
		{
			if (deltaP != 0 && skip(-deltaP) < -deltaP)
				return -1;

			read = read(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
		}

		else if (buffer.position() - deltaP >= 0) // read from buffer
		{
			int bpos = buffer.position();
			int limit = buffer.limit();
			buffer.limit(bpos);
			buffer.position(buffer.position() - deltaP);
			read = Math.min(buf.remaining(), buffer.remaining());
			buffer.get(buf.array(), buf.arrayOffset() + buf.position(), read);
			buffer.position(bpos);
			buffer.limit(limit);
		} else
		{ // no longer in buffer (rewind)

			long currentPos = chan.position();
			chan.position(inputPosition);
			int bpos = buf.position();
			read = chan.read(buf);
			buf.position(bpos);
			chan.position(currentPos);
		}

		if (read > 0) // update limit
			buf.limit(buf.position() + read);

		return read;
	}

	synchronized long skip(long inputPosition, long n) throws IOException
	{
		long toSkip = position - inputPosition;

		if (toSkip >= 0)
			return this.skip(n - toSkip);

		return this.skip(-toSkip) + this.skip(n);
	}

	private synchronized long skip(long n) throws IOException
	{
		final byte[] b = new byte[8192];
		final long skip = n;
		int read = 0;
		while (n > 0)
		{
			read = this.read(b, 0, (int)Math.min(b.length, n));
			if (read < 0)
				break;
			n -= read;
		}

		return skip - n;
	}

	synchronized int available(long inputPosition) throws IOException
	{
		int bufferPos = (int)(position - inputPosition);
		if (bufferPos < 0)
			return 0;
		if (bufferPos < buffer.limit())
			return buffer.remaining();
		return Math.max(0, in.available() - bufferPos);
	}

	static CacheStreamLoader getStream(final InputStream in, SeekableByteChannel out, String uid, long startpos, boolean autoload)
	{
		synchronized (cacheLoader)
		{
			CacheStreamLoader csl = cacheLoader.get(uid);
			if (csl == null)
			{
				csl = new CacheStreamLoader(in, out, uid, startpos, autoload);
				cacheLoader.put(uid, csl);
			}
			csl.streamCount++;
			return csl;
		}

	}

	static CacheStreamLoader getStream(String uid)
	{
		synchronized (cacheLoader)
		{
			final CacheStreamLoader csl = cacheLoader.get(uid);

			if (csl != null)
				csl.streamCount++;

			return csl;
		}
	}
}
