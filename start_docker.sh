#!/bin/bash

# Create correct symbolic links to neo4j and data directories
# (edit as appropriate for local environment)
ln -s /dtmp/`whoami`/neo4j neo4j
ln -s /dtmp/`whoami`/data data

# Change permissions to make things easy
chmod +x start_docker.sh
chmod 777 -R notebooks
chmod 777 -R neo4j
chmod 777 -R data
chmod 777 requirements.txt

# Export some environmental variables for Docker
export UID=$(id -u)
export GID=$(id -g)
export HOSTNAME=$(hostname)
export DNSDOMAINNAME=$(dnsdomainname)

# Say what we did
echo " Created symbolic links:"
echo "    neo4j -> /dtmp/`whoami`/neo4j"
echo "    data -> /dtmp/`whoami`/data"
echo " Set environmental variables:"
echo "    UID=$UID"
echo "    GID=$GID"
echo "    HOSTNAME=$HOSTNAME"
echo "    DNSDOMAINNAME=$DNSDOMAINNAME"

docker-compose build
docker-compose up

