# Scaling Science
## Overview
We are leveraging methods from machine learning, artificial intelligence, databases, and graph theory to improve our ability to compute on scientific knowledge—and thus more intelligently plan, collaborate, and allocate resources to science. The end goal is the creation of a decentralized, community-focused scientific knowledge dissemination platform.

## Data
For now, we are working with the publication metadata released by the [Open Academic Graph project](https://www.openacademic.ai/oag/). This is a joining of the [Microsoft Academic Graph](https://www.microsoft.com/en-us/research/project/microsoft-academic-graph/) and [AMiner database](https://aminer.org/).

In the future, we will move to active scraping and integration of [ArXiv](https://arxiv.org/), [BioRxiv](https://www.biorxiv.org/), [The PMC Open Access Subset](https://www.ncbi.nlm.nih.gov/pmc/tools/openftlist/), and other data sources. Of course, the end goal will be a platform that scientists submit work and results to directly, similar to Github.

## Version control
Git. Please submit issues for new features and bugs, create branches when working on issues, and submit pull requests for merging back into master so everyone is synchronized.

## Project management
We are using [Github Projects](https://github.com/jameswweis/scaling-science/projects) to coordinate the work and issues in the different branches of this project.


## Quick start

Run `./start_docker.sh` to read/set some environmental variables and build and launch `neo4j` and `jupyter notebook` containers. Console will print a token-based authentication link to paste into your browser to connect to both the `jupyter` session and the `neo4j` broser portal. The [Neo4j](https://neo4j.com/) database is password protected via the credentials in the `NEO4J_AUTH` environmental variable set in `docker-compose.yml`.


## Database Schema

### Nodes

#### :Author
[Relationships](#relationships)  
(:Author)-[:AUTHORED]->(:Quanta)   
(:Author)-[:COAUTHOR]-(:Author)   
 (:Author)-[:AFFILIATED_WITH]->(:Organization)  
(:Author)-[:METRICS_IN]->(:Year)

|property       |type   |index|unique|description|
|---------------|-------|-----|------|-----------|
|temporary      |INTEGER|     |      |           |
|start_year     |INTEGER|     |      | year the author's first paper was published           |
|idMag          |STRING |     |      |           |
|cleanName      |STRING |TRUE |      |           |
|citations      |INTEGER|     |      |           |
|name           |STRING |     |      |           |
|hIndex         |INTEGER|     |      |           |
|id             |STRING |TRUE |TRUE  |           |
|position       |STRING |     |      |deprecated - part of :AUTHORED relt            |
|normalizedName |STRING |     |      |           |
|lastAffiliation|STRING |     |      |           |
|numPublications|INTEGER|     |      |           |
|idAminer       |STRING |     |      |           |


#### :Quanta
[Relationships](#relationships)  
(:Author)-[:AUTHORED]->(:Quanta)  
(:Quanta)-[:METRICS_IN]->(:Year)  
(:Quanta)-[:PUBLISHED_IN]->(:Year)  
(:Quanta)-[:CITES]->(:Quanta)  
(:Quanta)-[:PUBLISHED_IN]->(:Venue)

|property       |type   |index|unique|description|
|:-------------:|:-----:|:---:|:----:|:---------:|
|   temporary   | FLOAT |     |      |           |
|     issue     |STRING |     |      |           |
|     year      |INTEGER|     |      |           |
|    docType    |STRING |     |      |           |
|     idv1      |STRING |TRUE |      |           |
|   language    |STRING |     |      |           |
|   abstract    |STRING |     |      |           |
|     title     |STRING |     |      |           |
|     idMag     |STRING |     |      |           |
|    volume     |STRING |     |      |           |
|   cleanName   |STRING |TRUE |      |           |
|   citations   |INTEGER|     |      |           |
|   publisher   |STRING |     |      |           |
|      id       |STRING |TRUE | TRUE |           |
|   idAminer    |STRING |     |      |           |
|      doi      |STRING |     |      |           |


#### :Venue
(:Venue)-[:METRICS_IN]-(:Year)  
(:Quanta)-[:PUBLISHED_IN]->(:Venue)  

|property       |type   |index|unique|description|
|:-------------:|:-----:|:---:|:----:|:---------:|
|   cleanName   |STRING |TRUE |      |           |
|   journalId   |STRING |     |      |           |
|     name      |STRING |     |      |           |
|      id       |STRING |TRUE | TRUE |           |
|normalizedName |STRING |     |      |           |
|     idMag     |STRING |     |      |           |
|  matchingId   |STRING |TRUE |      |           |
|   idAminer    |STRING |     |      |           |

#### :Year
(:Quanta)-[:PUBLISHED_IN]->(:Year)  
(:Quanta)-[:METRICS_IN]->(:Year)  
(:Author)-[:METRICS_IN]->(:Year)  
(:Venue)-[:METRICS_IN]-(:Year)


|property       |type   |index|unique|description|
|:-------------:|:-----:|:---:|:----:|:---------:|
|     value     |INTEGER|FALSE|FALSE |           |


## Relationships

#### (:Author)-[:AUTHORED]->(:Quanta)

#### (:Author)-[:COAUTHOR]-(:Author)

#### (:Author)-[:AFFILIATED_WITH]->(:Organization)

#### (:Author)-[:METRICS_IN]->(:Year)
|property                       |type   |description|
|-------------------------------|-------|-----------|
|authorAge                      |INTEGER| Years since first paper published         |
|citations                      |INTEGER|           |
|citationsPerPaper              |FLOAT  |           |
|citationsPerPaperDelta         |FLOAT  | Change in mean cites per paper over last two years          |
|hIndex                         |INTEGER|           |
|hIndexDelta                    |INTEGER| Change in h-index over the last two years           |
|maxCitations                   |INTEGER|Max citations on a single paper author has received          |
|pageRank                       |FLOAT  | PageRank in coauthor graph          |
|rankCitationsPerPaper          |FLOAT  | Rank of author (between 0 and 1) among all other authors in terms of mean citations per year          |
|recentCoauthors                |INTEGER| Total number of coauthors in last two years           |
|totalCitations                 |INTEGER|           |
|totalCitationsDelta            |INTEGER| Two year change in total citations          |
|totalPapers                    |INTEGER|           |
|totalPapersDelta               |INTEGER|           |
|totalVenues                    |INTEGER| Total number of venues author published in          |
|venueCitationsPerPaperDeltaMax |FLOAT  |           |
|venueCitationsPerPaperDeltaMean|FLOAT  |Two year change in mean citations per paper of venues author has published in            |
|venueCitationsPerPaperDeltaMin |FLOAT  |           |
|venueCitationsPerPaperMax      |FLOAT  |           |
|venueCitationsPerPaperMean     |FLOAT  |Mean citations per paper of venues author has published in           |
|venueCitationsPerPaperMin      |FLOAT  |           |
|venueHIndexDeltaMax            |INTEGER|           |
|venueHIndexDeltaMean           |FLOAT  | Mean of 2-year h-index change for venues author has published in          |
|venueHIndexDeltaMin            |INTEGER|           |
|venueHIndexMax                 |INTEGER|           |
|venueHIndexMean                |FLOAT  | Mean of H-indices of venues author has published in          |
|venueHIndexMin                 |INTEGER|           |
|venueMaxCitationsMax           |INTEGER|           |
|venueMaxCitationsMean          |FLOAT  | Mean of maximum number of citations any paper published in a venue has received for each venue the author has published in          |
|venueMaxCitationsMin           |INTEGER|           |
|venueRankCitationsPerPaperMin  |FLOAT  |           |
|venueRankCitationsPerPaperMean |FLOAT  | Ranks of venues (between 0-1) in which the author has published determined by mean number of citations per paper          |
|venueRankCitationsPerPaperMax  |FLOAT  |           |
|venueTotalPapersDeltaMax       |INTEGER|           |
|venueTotalPapersDeltaMean      |FLOAT  | Two year change in mean of the total number of papers published in venues author has published in          |
|venueTotalPapersDeltaMin       |INTEGER|           |
|venueTotalPapersMax            |INTEGER|           |
|venueTotalPapersMean           |FLOAT  | Mean of the total number of papers published in venues author has published in           |
|venueTotalPapersMin            |INTEGER|           |
|weightedPageRank               |FLOAT  | In coauthor network          |



#### (:Quanta)-[:METRICS_IN]->(:Year)

#### (:Quanta)-[:PUBLISHED_IN]->(:Year)

#### (:Quanta)-[:CITES]->(:Quanta)

#### (:Quanta)-[:PUBLISHED_IN]->(:Venue)

#### (:Venue)-[:METRICS_IN]-(:Year)
