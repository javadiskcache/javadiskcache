/*
	This file is part of the d3fact Project "javadiskcache".
	Copyright (C) 2007-2012 d3fact Project Team

	This library is subject to the terms of the Mozilla Public License, v. 2.0.
	You should have received a copy of the MPL along with this library; see the
	file LICENSE. If not, you can obtain one at http://mozilla.org/MPL/2.0/.
*/
package github.funsheep.javadiskcache;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * URL wrapper for the {@link ICacheable} interface.
 * Uses the light weight {@link URLConnection} to retrieve size and lastModified time stamp. Only accesses the input stream if
 * no proper version is found in the {@link FileCache}.
 * @author funsheep
 */
public class CacheableURL implements ICacheable
{
	
	/** Constant that specifies the timeout when connecting to the source the url points at. Can be changed when needed. */
	public static int NETWORK_TIMEOUT = 10 * 1000;

	private static final Logger LOGGER = Logger.getLogger();
	

	private final URL url;
	private URLConnection connection;
	

	/**
	 * Constructor.
	 * @param url The url to cache.
	 */
	public CacheableURL(URL url)
	{
		this.url = url;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String uID()
	{
		return this.url.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long size()
	{
		URLConnection con = this.getCon();
		if (con == null)
			return UNKNOWN;
		long size = con.getContentLengthLong();
		if (size == -1)
			return UNKNOWN;
		return size;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long lastModified()
	{
		URLConnection con = this.getCon();
		if (con == null)
			return UNKNOWN;
		long lm = con.getLastModified();
		if (lm == 0)
			return UNKNOWN;
		return lm;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InputStream requestContent() throws IOException
	{
		URLConnection con = this.getCon();
		if (con == null)
			return null;
		return con.getInputStream();
	}

	/**
	 * Method to get the url connection. Stored if connect was successful.
	 * If not successfully connected, <code>null</code> is returned.
	 * @return URLConnection object on successful connect, <code>null</code> otherwise.
	 */
	private final URLConnection getCon()
	{
		if (this.connection == null)
		{
			try
			{
				this.connection = this.url.openConnection();
				this.connection.setConnectTimeout(NETWORK_TIMEOUT);
				this.connection.setReadTimeout(NETWORK_TIMEOUT);
				this.connection.setUseCaches(false);
				this.connection.connect();
				// check html header for existence (200 = OK)
				if (!this.connection.getHeaderField(0).equals("HTTP/1.1 200 OK"))
					this.connection = null;
			}
			catch (IOException ex)
			{
				LOGGER.warn("Could not establish connection to url " + this.url, ex);
				this.connection = null;
			}
		}
		return this.connection;
	}

}
