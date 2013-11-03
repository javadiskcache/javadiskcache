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
import java.io.IOException;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

/**
 * Helper functions used throughout the project.
 * @author funsheep
 */
class Tools
{

	private final static Logger LOGGER = Logger.getLogger();


	/**
	 * Closes a {@link Closeable} while catching the exception. Checks for <code>null</code> reference.
	 * @param s The {@link Closeable} to close. May be <code>null</code>. 
	 */
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

	/**
	 * Converts a given array of longs into their byte representation.
	 * @param values The long values to convert.
	 * @return An array containing the byte representations of the given long values.
	 */
	public static byte[] longToByte(long... values)
	{
		byte[] data = new byte[8 * values.length];
		for (int s = 0; s < values.length; s++)
			for (int i = 0; i < 8; i++)
				data[i + s * 8] = (byte)((values[s] >>> (0 + i * 8)) & 0xff);
		return data;
	}


	/**
	 * Converts a given array of bytes into an URL save string (RFC 3548). 
	 * This method does the same as calling {@link #toBase64String(byte[])} with {@link String#getBytes()}.
	 * @param a A string to convert.
	 * @return A base64 representation of the given string.
	 */
	public static String toBase64String(String a) // RFC 3548
	{
		return toBase64String(a.getBytes());
	}

	/**
	 * Converts a given array of bytes into an URL save string (RFC 3548). 
	 * @param b The byte array to convert to base64.
	 * @return A bes64 representation of the given bytes.
	 */
	public static String toBase64String(byte[] b) // RFC 3548
	{
		return Base64.encodeBase64URLSafeString(b);
	}

	/**
	 * Convert a base64 string representation into the native byte representation.
	 * This conversion method works as in RFC 3548 specified.
	 * '/' are replaced by '_' and '+' are replaced by '-' before converting into the native
	 * byte representation.
	 * @param string The string to convert.
	 * @return
	 */
	public static byte[] fromBase64String(String string) // RFC 3548
	{
		return Base64.decodeBase64(string.replace("/", "_").replace("+", "-"));
	}

	/**
	 * Returns a temporary directory custom to the current user.
	 * It checks whether the directory exists and is writable for the current user.
	 * The directory is located in the systems temporary directory (system preference <code>java.io.tmpdir</code>).
	 * The directory's name is the user name (system preference <code>user.name</code>) converted to hex code.
	 * Using the users name ensures that he can write to that directory on multi-user systems.
	 * @return A unique (and custom) temporary directory. Ensures that it exists and is writable. Otherwise throws IOException.
	 * @throws IOException if directory is not available.
	 */
	public static final String ensureTempDir() throws IOException
	{
		StringBuilder sb = new StringBuilder(30);
		String tmpdir = System.getProperty("java.io.tmpdir");
		if (tmpdir.charAt(tmpdir.length() - 1) != File.separatorChar)
			tmpdir += File.separatorChar;
		sb.append(tmpdir);
		sb.append(Hex.encodeHexString(System.getProperty("user.name").getBytes()));
		sb.append(File.separatorChar);
		String userTmpDir = sb.toString();

		File path = new File(userTmpDir);
		if (!path.exists() && !path.mkdirs())
		{
			userTmpDir = tmpdir;
			throw new IOException("Could not create directory structure for path: " + path.toString() + " !");
		}
		LOGGER.info("Using temporary directory: " + userTmpDir);
		return userTmpDir;
	}


	private Tools()
	{
		// no instance
	}

}
