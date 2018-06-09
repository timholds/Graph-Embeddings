# Scaling Science

## Dependencies and documentation

### Database 
[JanusGraph](http://janusgraph.org/) with an [HBase](https://hbase.apache.org/) backend running in [Docker](https://www.docker.com/).

### Graph traversal
We are using [gremlin-scala](https://github.com/mpollmeier/gremlin-scala), which provides a wrapper allowing the use of [Apache Tinkerpop 3](https://github.com/apache/tinkerpop) from Scala.  

### Language
Written in [Scala 2.12](https://www.scala-lang.org/) and JDK1.8 using [sbt](https://www.scala-sbt.org/) to manage dependencies (see build.sbt) and compilation. 

### IDE
[IntelliJ IDEA Ultimate](https://www.jetbrains.com/idea/), which is [provided for free](https://www.jetbrains.com/idea/) to students and faculty members.

### Data source
For now, we are working with the publication metadata released by the [Open Academic Graph project](https://www.openacademic.ai/oag/). This is a joining of the [Microsoft Academic Graph](https://www.microsoft.com/en-us/research/project/microsoft-academic-graph/) and [AMiner database](https://aminer.org/). 
