#!/bin/bash

chmod 777 -R notebooks
chmod 777 -R neo4j
chmod 777 requirements.txt

export UID=$(id -u)
export GID=$(id -g)
export HOSTNAME=$(hostname)
export DNSDOMAINNAME=$(dnsdomainname)

echo " Setting env var..."
echo "    UID=$UID"
echo "    GID=$GID"
echo "    HOSTNAME=$HOSTNAME"
echo "    DNSDOMAINNAME=$DNSDOMAINNAME"

docker-compose build
docker-compose up

