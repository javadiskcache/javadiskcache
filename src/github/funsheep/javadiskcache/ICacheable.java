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

/**
 * Convenience-Interface for objects that can be cached by the {@link FileCache}.
 * The idea is to search for a cached file by {@link #uID()}, {@link #size()} and {@link #lastModified()}, if not found, finally the {@link #content()}
 * from the original source is request. This is especially useful when creating the {@link InputStream} is very expensive.
 * @author funsheep
 */
public interface ICacheable
{

	/**
	 * Constant indicating that the {@link #size()} or the {@link #lastModified()} time stamp is unknown.
	 */
	public static final long UNKNOWN = FileCache.NOT_AVAILABLE;

	/**
	 * The uID for this object as defined for {@link FileCache#getCachedInputStream(String, InputStream, long, long)}. Usually an URI or the like.
	 * @return The uID for this object
	 */
	public String uID();

	/**
	 * The current size of this content.
	 * @return The current size of the content or {@link #UNKNOWN} if not known or available.
	 * @throws Throws an IOException if
	 */
	public long size();

	/**
	 * The 'last modified' time stamp for the content.
	 * @return The 'last modified' time stamp for the content or {@link #UNKNOWN} if not known or available.
	 */
	public long lastModified();

	/**
	 * Requests the actual content for caching. This method is called only when no cached file could be found.
	 * @return An {@link InputStream} that provides access to the actual content.
	 */
	public InputStream requestContent() throws IOException;

}
