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
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Logger implementation which can be used with asserts.
 * @author funsheep
 */
class Logger
{

	private static final String					CLASSNAME	= Logger.class.getName();
	private static final Logger					ROOT		= new Logger("");

	private final java.util.logging.Logger	logger;


	/**
	 * Constructor.
	 * @param name
	 */
	protected Logger(final String name)
	{
		this.logger = java.util.logging.Logger.getLogger(name);
	}

	/**
	 * Log on the {@link Level#FINEST} level.
	 * @param s
	 * @return true
	 */
	public boolean finest(final String s)
	{
		return this.log(Level.FINEST, s, null, (Object[])null);
	}

	/**
	 * Log on the {@link Level#FINEST} level. Stringsequences like '{X}' (where X is a number greater than null) are replaced by the objects string representation of
	 * the object at index X in the given array.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean finest(final String s, Object ... os)
	{
		return this.log(Level.FINEST, s, os);
	}


	/**
	 * Log on the {@link Level#FINER} level.
	 * @param s
	 * @return true
	 */
	public boolean finer(final String s)
	{
		return this.log(Level.FINER, s, null, (Object[])null);
	}

	/**
	 * Log on the {@link Level#FINER} level. Stringsequences like '{X}' (where X is a number greater than null) are replaced by the objects string representation of
	 * the object at index X in the given array.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean finer(final String s, Object ... os)
	{
		return this.log(Level.FINER, s, os);
	}

	/**
	 * Log on the {@link Level#FINE} level.
	 * @param s
	 * @return true
	 */
	public boolean fine(final String s)
	{
		return this.log(Level.FINE, s, null, (Object[])null);
	}

	/**
	 * Log on the {@link Level#FINE} level.
	 * @param s
	 * @param throwable
	 * @return true
	 */
	public boolean fine(final String s, final Throwable throwable)
	{
		return this.log(Level.FINE, s, throwable, (Object[])null);
	}

	/**
	 * Log on the {@link Level#FINE} level. Stringsequences like '{X}' (where X is a number greater than null) are replaced by the objects string representation of
	 * the object at index X in the given array.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean fine(final String s, Object ... os)
	{
		return this.log(Level.FINE, s, os);
	}

	/**
	 * Log on the {@link Level#INFO} level.
	 * @param s
	 * @return true
	 */
	public boolean info(final String s)
	{
		return this.log(Level.INFO, s, null, (Object[])null);
	}

	/**
	 * Log on the {@link Level#INFO} level.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean info(final String s, final Throwable e)
	{
		return this.log(Level.INFO, s, e, (Object[])null);
	}

	/**
	 * Log on the {@link Level#INFO} level. Stringsequences like '{X}' (where X is a number greater than null) are replaced by the objects string representation of
	 * the object at index X in the given array.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean info(final String s, Object ... os)
	{
		return this.log(Level.INFO, s, os);
	}

	/**
	 * Log on the {@link Level#WARNING} level.
	 * @param s
	 * @return true
	 */
	public boolean warn(final String s)
	{
		return this.log(Level.WARNING, s, null, (Object[])null);
	}

	/**
	 * Log on the {@link Level#WARNING} level.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean warn(final String s, final Throwable e)
	{
		return this.log(Level.WARNING, s, e, (Object[])null);
	}

	/**
	 * Log on the {@link Level#WARNING} level. Stringsequences like '{X}' (where X is a number greater than null) are replaced by the objects string representation of
	 * the object at index X in the given array.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean WARNING(final String s, Object ... os)
	{
		return this.log(Level.WARNING, s, os);
	}

	/**
	 * Log on the {@link Level#SEVERE} level.
	 * @param s
	 * @return true
	 */
	public boolean severe(final String s)
	{
		return this.log(Level.SEVERE, s, null, (Object[])null);
	}

	/**
	 * Log on the {@link Level#SEVERE} level.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean severe(final String s, final Throwable e)
	{
		return this.log(Level.SEVERE, s, e, (Object[])null);
	}

	/**
	 * Log on the {@link Level#SEVERE} level. Stringsequences like '{X}' (where X is a number greater than null) are replaced by the objects string representation of
	 * the object at index X in the given array.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean severe(final String s, Object ... os)
	{
		return this.log(Level.SEVERE, s, os);
	}

	/**
	 * Log on the {@link Level#FINE} level.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean debug(final String s, final Throwable e)
	{
		return this.log(Level.FINE, s, e, (Object[])null);
	}

	/**
	 * Log on the {@link Level#FINE} level.
	 * @param s
	 * @param e
	 * @return true
	 */
	public boolean debug(final String s)
	{
		return this.log(Level.FINE, s, (Object[])null);
	}

	/**
	 * Log on the given level.
	 * @param l
	 * @param msg
	 * @return true
	 */
	public boolean log(final Level l, final String msg)
	{
		return this.log(l, msg, null, (Object[])null);
	}

	/**
	 * Log on the given level. Stringsequences like '{X}' (where X is a number greater than null) are replaced by the objects string representation of
	 * the object at index X in the given array.
	 * @param l
	 * @param msg
	 * @param o
	 * @return true
	 */
	public boolean log(final Level l, final String msg, final Object... o)
	{
		return this.log(l, msg, null, o);
	}

	/**
	 * Log on the given level.
	 * @param l
	 * @param msg
	 * @param t
	 * @return true
	 */
	public boolean log(final Level l, final String msg, final Throwable t)
	{
		return this.log(l, msg, t, (Object[])null);
	}

	/**
	 * Log on the given level. Stringsequences like '{X}' (where X is a number greater than null) are replaced by the objects string representation of
	 * the object at index X in the given array.
	 * @param l
	 * @param msg
	 * @param t
	 * @param o
	 * @return true
	 */
	public boolean log(final Level l, final String msg, final Throwable t, final Object... o)
	{
		if (!this.logger.isLoggable(l))
			return true;

		final StackTraceElement st = getStackItem();
		final LogRecord r = new LogRecord(l, msg);
		r.setSourceClassName(st.getClassName());
		r.setSourceMethodName(st.getMethodName());
		r.setThrown(t);
		r.setParameters(o);
		this.logger.log(r);
		return true;
	}

	/**
	 * Returns the calling class' name.
	 *
	 * @return the calling class' name.
	 * @see #getStackItem()
	 */
	protected static String getClassName()
	{
		final StackTraceElement st = getStackItem();
		return st.getClassName();
	}

	/**
	 * Returns the calling class' packagename.
	 *
	 * @return the calling class' packagename.
	 * @see #getClassName
	 */
	protected static String getPackageName()
	{
		final String s = getClassName();
		final int i = s.lastIndexOf('.');
		return i < 0 ? s : s.substring(0, i);
	}

	/**
	 * Returns the first stack element which is not part of the Logger hierarchie.
	 *
	 * @return the first stack element which is not part of the Logger hierarchie
	 */
	private static StackTraceElement getStackItem()
	{
		final StackTraceElement[] st = new Exception().getStackTrace();

		int i = 1;
		while (i < st.length)
		{
			final String n = st[i].getClassName();
			if (CLASSNAME.equals(n))
				break;
			i++;
		}
		i++; // skip first occurence of class
		while (i < st.length)
		{
			final String n = st[i].getClassName();
			if (!CLASSNAME.equals(n))
				return st[i];
			i++;
		}

		throw new RuntimeException("the roof is on fire");
	}

	/**
	 * Returns a new logger instance. The logger automatically detects the name of the calling
	 * class.
	 *
	 * @return a new logger instance
	 */
	public static Logger getLogger()
	{
		return new Logger(getClassName());
	}

	/**
	 * Returns a new logger instance. The logger automatically detects it's calling class'
	 * packagename.
	 *
	 * @return a new logger instance
	 */
	public static Logger getPackageLogger()
	{
		return new Logger(getPackageName());
	}

	/**
	 * Discover whether or not this logger is sending its output to its parent logger.
	 *
	 * @return true if output is to be sent to the logger's parent
	 */
	public synchronized boolean getUseParentHandlers()
	{
		return this.logger.getUseParentHandlers();
	}

	/**
	 * Returns a new logger instance.
	 *
	 * @param c class to obtain the name from
	 * @return a new logger instance.
	 */
	public static <T> Logger getLogger(final Class<T> c)
	{
		return new Logger(c.getName());
	}

	/**
	 * Returns a new logger instance.
	 *
	 * @param c class to obtain it's package name from
	 * @return a new logger instance.
	 */
	public static <T> Logger getPackageLogger(final Class<T> c)
	{
		return new Logger(c.getPackage().getName());
	}

	/**
	 * All Handlers are only applied to the root level.
	 *
	 * @param handler
	 * @return <code>true</code> (always)
	 */
	public static boolean addHandler(final Handler handler)
	{
		ROOT.logger.addHandler(handler);
		return true;
	}

	/**
	 * All Handlers are only applied to the root level.
	 *
	 * @param handler
	 * @return <code>true</code> (always)
	 */
	public static boolean removeHandler(final Handler handler)
	{
		ROOT.logger.removeHandler(handler);
		return true;
	}

	/**
	 * Sets the level of the root Logger and its ConsoleHandler
	 *
	 * @param level new logging level
	 * @return true
	 */
	public boolean setLevel(final Level level)
	{
		this.logger.setLevel(level);
		return true;
	}

	/**
	 * Sets the formatter of the ConsoleHandler of the rootLogger.
	 *
	 * @param formatter the formatter to set
	 * @return <code>true</code> (always)
	 */
	public static boolean setFormatter(final Formatter formatter)
	{
		final Handler[] handlers = Logger.getHandlers();
		for (final Handler element : handlers)
			element.setFormatter(formatter);
		return true;
	}

	/**
	 * Returns the {@link #ROOT} logger's handlers.
	 *
	 * @return the {@link #ROOT} logger's handlers.
	 * @see java.util.logging.Logger
	 */
	public static Handler[] getHandlers()
	{
		return ROOT.logger.getHandlers();
	}

	/**
	 * Sets a filter.
	 * @param filter
	 * @return <code>true</code> (always)
	 * @see java.util.logging.Logger#setFilter(Filter)
	 */
	public boolean setFilter(final Filter filter)
	{
		this.logger.setFilter(filter);
		return true;
	}

	/**
	 * Returns <code>true</code>.
	 *
	 * @param config inputstream to read configuration data from.
	 * @return <code>true</code>
	 * @throws IOException if an IO error occured
	 * @throws SecurityException if the caller is not allowed to perform this operation
	 */
	public static boolean loadConfiguration(final InputStream config) throws IOException, SecurityException
	{
		LogManager.getLogManager().readConfiguration(config);
		return true;
	}

}
