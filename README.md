# Scaling Science
## Overview
We are leveraging methods from machine learning, artificial intelligence, databases, and graph theory to improve our ability to compute on scientific knowledge—and thus more intelligently plan, collaborate, and allocate resources to science. The end goal is the creation of a decentralized, community-focused scientific knowledge dissemination platform.

## Data
For now, we are working with the publication metadata released by the [Open Academic Graph project](https://www.openacademic.ai/oag/). This is a joining of the [Microsoft Academic Graph](https://www.microsoft.com/en-us/research/project/microsoft-academic-graph/) and [AMiner database](https://aminer.org/).

In the future, we will move to active scraping and integration of [ArXiv](https://arxiv.org/), [BioRxiv](https://www.biorxiv.org/), [The PMC Open Access Subset](https://www.ncbi.nlm.nih.gov/pmc/tools/openftlist/), and other data sources. Of course, the end goal will be a platform that scientists submit work and results to directly, similar to Github. 

## Version control
Git, with `Jupyter Notebook`s always being stripped of output before committing (run `pip install --upgrade nbstripout && nbstripout --install`, see #29). Please submit issues for new features and bugs, create branches when working on issues, and submit pull requests for merging back into master so everyone is synchronized. 

## Project management
We are using [Github Projects](https://github.com/jameswweis/scaling-science/projects) to coordinate the work and issues in the different branches of this project. 


## Quick start

Run `./start_docker.sh` to read/set some environmental variables and build and launch `neo4j` and `jupyter notebook` containers. Console will print a token-based authentication link to paste into your browser to connect to both the `jupyter` session and the `neo4j` broser portal. The [Neo4j](https://neo4j.com/) database is password protected via the credentials in the `NEO4J_AUTH` environmental variable set in `docker-compose.yml`. 



