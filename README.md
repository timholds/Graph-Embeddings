# Scaling Science
## Overview
We are leveraging methods from machine learning, artificial intelligence, databases, and graph theory to improve our ability to compute on scientific knowledge—and thus more intelligently plan, collaborate, and allocate resources to science. The end goal is the creation of a decentralized, community-focused scientific knowledge dissemination platform.

## Data
For now, we are working with the publication metadata released by the [Open Academic Graph project](https://www.openacademic.ai/oag/). This is a joining of the [Microsoft Academic Graph](https://www.microsoft.com/en-us/research/project/microsoft-academic-graph/) and [AMiner database](https://aminer.org/).

In the future, we will move to active scraping and integration of [ArXiv](https://arxiv.org/), [BioRxiv](https://www.biorxiv.org/), [The PMC Open Access Subset](https://www.ncbi.nlm.nih.gov/pmc/tools/openftlist/), and other data sources. Of course, the end goal will be a platform that scientists submit work and results to directly, similar to Github. 

## Dependencies and documentation

### Database 
[JanusGraph](http://janusgraph.org/) with an [HBase](https://hbase.apache.org/) backend running in [Docker](https://www.docker.com/).

### Graph traversal
We are using [gremlin-scala](https://github.com/mpollmeier/gremlin-scala), which provides a wrapper allowing the use of [Apache Tinkerpop 3](https://github.com/apache/tinkerpop) from Scala.  

### Language
Written in [Scala 2.12](https://www.scala-lang.org/) and JDK1.8 using [sbt](https://www.scala-sbt.org/) to manage dependencies (see build.sbt) and compilation. 

### IDE
[IntelliJ IDEA Ultimate](https://www.jetbrains.com/idea/), which is [provided for free](https://www.jetbrains.com/idea/) to students and faculty members. See ./.idea. 

### Version control
Git, obviously. As our team scales, leveraging proper best practices will become increasingly important. Please submit issues for new features and bugs, create branches when working on issues, and submit pull requests for merging back into master so everyone is synchronized. 

### Project management
We are starting to use [Github Projects](https://github.com/jameswweis/scaling-science/projects) to coordinate the work and issues in the different branches of this project. 
