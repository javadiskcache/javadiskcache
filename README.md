# javadiskcache #

A robust disk cache that works over several parallel running jVMs.

 
## Features ##

 * Transparent fileCache for InputStream with simple wrapping mechanism.
 * Detects changes to the original source using its size and its 'last modified' time stamp.
 * Detects multiple stream requests for a specific source and automatically redirects them to a single request (only one request per source is made).
 * Checks free-space on drive prior to storing a file.
 * Supports `java.io.InputStream` and `java.nio.channels.ReadableByteChannel`
 * Multi-Threaded Support and Multi-jVM support. That means, the file cache is thread-save but also jVM-save. Several java instances use the same cache. This works for arbitrarily requested sources. Only the source ID has to be identical.
 * Global cache-size limit with LRU-Schema (also works over several jVMs).

## Usage ##

### Setup ###
 
 The standard settings are:
  * the cache can use up to one GB of disk space
  * caching directory is 'cache'

 However, initially, these values can be changed with
 
```java
FileCache.setup(String, long)
```
 
 For example, to setup a cache that uses an application specific cache directory 'myapp' and uses one MB at max. simply call
 
```java
FileCache.setup("myapp", 1 * 1024 * 1024);
```
 
 before any cache request!
 
### Request and Cache Content ###
 
 The file cache provides two arbitrary methods to request content:
  * `java.io.InputStream`
  * `java.nio.channels.ReadableByteChannel`

 A source is identified by its unique ID (String). This ID must be identical over all running java instances to work properly.  
 To identify changes made to the original source, the cache relies on the size of the source and its 'last modified' time stamp.
 If either of them differs from the cached file, the new content is requested and cached.
 If the source is no longer available (e.g. a connection break down), the cached content is returned.
 
 To cache a specific content, simply call the `getCachedInputStream` or `getCachedByteChannel` to either cache an input stream or an byte channel.
 Both methods require that initial access to the original content (via inputstream, channel), the size and the last modified time stamp are provided.
 Sometimes, initially providing an input stream or channel without even requesting its content can be resource (and time) consuming.

 Therefore, it can be much more efficient to first check if the specific content is cached or not.
 This can be done by:
 
```java
String uID = ...; //unique id for the source
long size = ...; //size of the source content in bytes
long timestamp = ...; //last modified time stamp of the content
InputStream cached = FileCache.instance().getCachedInputStream(uID, null, size, timestamp);
if (cached == null)
{
	 InputStream content = ...; //open connection to the source to actually cache the content
	 cached = FileCache.instance().getCachedInputStream(uID, content, size, timestamp);
}
// ... use 'cached' here
```
 
An implementation of this mechanism is readily provided by the `ICacheable` interface.
The package contains an URL wrapper for the `ICacheable` interface.
The wrapper class `CacheableURL` uses the lightweight URLConnection mechanism to retrieve size and 'last modified' time stamp.

## Requirements ##
 * JRE >= v1.7
 * commons-codec >= v1.8 (is issued under the Apache License v2.0)

## License ##
 Mozilla Public License v2.0

## Changelog ##

#### v0.9.0 ####
 * Initial Commit
 * Added `ICacheable` interface
 * Added `CacheableURL` wrapper
 * Issues: add more javadoc
