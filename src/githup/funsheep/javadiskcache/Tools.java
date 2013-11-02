/*
	This file is part of the java diskcache library.
	Copyright (C) 2005-2013 funsheep, cgrote

	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
*/
package githup.funsheep.javadiskcache;

import java.io.Closeable;
import java.io.File;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;

/**
 * Helper functions used throughout the project.
 * @author work
 */
class Tools
{

	private final static Logger LOGGER = Logger.getLogger();

	public static void close(Closeable s)
	{
		try
		{
			if (s != null)
				s.close();
			assert LOGGER.fine("Close(closeable)--> Close");

		}
		catch (final Exception e)
		{
			assert LOGGER.log(Level.INFO, "unimportant", e);
		}
	}


	private static final String HEXES = "0123456789ABCDEF";

	public static String getHex(byte[] raw)
	{
		final StringBuilder hex = new StringBuilder( 2 * raw.length );
		for ( final byte b : raw )
		{
			hex.append(HEXES.charAt((b & 0xF0) >> 4))
			   .append(HEXES.charAt((b & 0x0F)));
		}
		return hex.toString();
	}


	public static byte[] longToByte(long... values)
	{
		byte[] data = new byte[8 * values.length];
		for (int s = 0; s < values.length; s++)
			for (int i = 0; i < 8; i++)
				data[i + s * 8] = (byte)((values[s] >>> (0 + i * 8)) & 0xff);
		return data;
	}


	public static String toBase64String(String a) // RFC 3548
	{
		return toBase64String(a.getBytes());
	}

	public static String toBase64String(byte[] b) // RFC 3548
	{
		return Base64.encodeBase64URLSafeString(b);
	}

	public static byte[] fromBase64String(String string) // RFC 3548
	{
		return Base64.decodeBase64(string.replace("/", "_").replace("+", "-"));
	}


	private Tools()
	{
		// no instance
	}

}
