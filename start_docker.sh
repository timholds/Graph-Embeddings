#!/bin/bash

chmod +x start_docker.sh
chmod 777 -R notebooks
chmod 777 -R neo4j
chmod 777 -R data
chmod 777 requirements.txt

# FOR USE WITH MATLABER CLUSTER
chown -R `id -u`:mlusers neo4j
chown -R `id -u`:mlusers data

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

