#!/bin/bash

chmod 777 -R notebooks
chmod 777 -R neo4j

export UID=$(id -u)
export GID=$(id -g)
export HOSTNAME=$(hostname)
export DNSDOMAINNAME=$(dnsdomainname)

echo "    UID=$UID"
echo "    GID=$GID"
echo "    HOSTNAME=$HOSTNAME"
echo "    DNSDOMAINNAME=$DNSDOMAINNAME"

docker-compose up

