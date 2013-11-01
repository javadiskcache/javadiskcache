/**
 * 
 */
package funsheep.githup.javadiskcache;

import java.io.Closeable;
import java.io.File;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;

/**
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
			sb.append(getHex(System.getProperty("user.name").getBytes()));
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


	public Tools()
	{
		// no instance
	}

}
