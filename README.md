javadiskcache
=============

A robust disk cache that works over several parallel running jVMs.

 
Features:
=============

 * Transparent fileCache for InputStream with simple wrapping mechanism.
 * Detects multiple stream requests for a specific id and automatically closes additional ones.
 * Checks free-space on drive prior to storing a file.
 * Multi-Thread Support and Multi-JVM support.
 * Global cache-size limit with LRU-Schema.


Requirements
=============
 * JRE >= v1.7
 * commons-codec >= v1.8 (is issued under the Apache License v2.0)

License
=============
 Mozilla Public License v2.0