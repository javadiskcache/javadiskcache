/*
	This file is part of the d3fact common library.
	Copyright (C) 2005-2012 d3fact Project Team

	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
*/
package githup.funsheep.javadiskcache;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * Load first from channel than from Stream.
 */
public class HybridInputStream extends FilterInputStream
{
	private final SeekableByteChannel chan;
	private ByteBuffer buffer = ByteBuffer.allocate(8192);
	private long position = 0;
	private boolean EOF = false;

	protected HybridInputStream(InputStream in, SeekableByteChannel chan)
	{
		super(in);
		this.chan = chan;
		this.buffer.limit(0);
	}

	@Override
	public synchronized int read() throws IOException
	{
		if (!EOF)
		{
			if (!buffer.hasRemaining())
			{
				buffer.clear();
				int read;
				try
				{
					do
					{
						read = chan.read(buffer);
					} while (read == 0);
					if (read > 0)
						position += read;
				} catch (IOException e)
				{
					closeFile();
					return super.read();
				}
				buffer.flip();
			}

			if (buffer.hasRemaining())
				return buffer.get() & 0xff;

			closeFile();
		}

		return super.read();
	}

	@Override
	public int read(byte b[]) throws IOException
	{
		return this.read(b, 0, b.length);
	}

	@Override
	public int read(byte b[], int off, int len) throws IOException
	{
		if (!EOF)
		{
			if (!buffer.hasRemaining())
			{
				buffer.clear();
				int read;

				try
				{
					do
					{
						read = chan.read(buffer);
					} while (read == 0);
					if (read > 0)
						position += read;
				} catch (IOException e)
				{
					closeFile();
				}
				buffer.flip();
			}

			if (buffer.hasRemaining())
			{
				int read = Math.min(len, buffer.remaining());
				buffer.get(b, off, read);
				return read;
			}

			closeFile();
		}

		return super.read(b, off, len);
	}

	@Override
	public void close() throws IOException
	{
		super.close();
		EOF = true;
		Tools.close(chan);
		buffer = null;
	}

	@Override
	public int available() throws IOException
	{
		return buffer.remaining() + in.available();
	}

	@Override
	public long skip(long n) throws IOException
	{
		if (!EOF)
		{
			long skipped = 0;
			int remaining = (int)Math.min(buffer.remaining(), n);
			buffer.position(buffer.position() + remaining);
			n -= remaining;
			skipped += remaining;

			if (n > 0)
			{
				long oldPos = position;
				if(Long.MAX_VALUE - position > n)//anti overflow
					position += n;
				else
					position = Long.MAX_VALUE;

				try
				{
					if (n < chan.size())
					{
						chan.position(Math.min(chan.position() + n, chan.size()));
					}
				} catch (IOException e)
				{
					closeFile();
					return skipped;
				}

				long skip = closeFile();
				return Math.max(skipped, skipped + skip - oldPos);
			}

			return skipped;
		}

		return super.skip(n);
	}

	private long closeFile() throws IOException
	{
		if(EOF)
			return 0;

		EOF = true;
		Tools.close(chan);
		buffer = null;

		return super.skip(position);
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
}
