# Overview

Spade format is an experimental Column-oriented data format (somewhat similar
to [Column-oriented DBMS](https://en.wikipedia.org/wiki/Column-oriented_DBMS) concept),
aimed at adequate encoding and decoding speeds to allow use for "in-flight" data,
and not just for "at rest" data like other existing formats such as 
[ORC](https://cwiki.apache.org/confluence/display/Hive/LanguageManual+ORC) and
[Parquet](https://parquet.apache.org/).

Aside from focus on more CPU-efficient encoding/decoding, differences include:

* Standard interfaces to expose data to processing as row-oriented
    * Processing systems need not be aware of underlying column format (can operate on streams of records/rows/Objects)
    * Processing systems can still do efficient projection at column level
* Both textual and binary encodings for trouble-shooting, interoperability
    * Since super-structure is the same, efficient transcoding possible

## Differences to ORC, Parquet

Some of the differences were already listed; other differences stemming from different goals, approach include:

* Smaller batch size: instead of targeting storage in tens-of-megs range (per encoded block), goal is to keep chunks in kilobyte range (less than a meg)
    * limits amount of memory needed per reader/writer process
    * lower latency between start of a chunk, production of a chunk
* With textual format, possible to operate (both decode AND encode) from platforms that do not handle binary well, such as Javascript.

## More

For more information (such as, say, format description...) check out [Wiki](../../wiki).
