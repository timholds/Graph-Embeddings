#!/bin/bash

# Create correct symbolic links to neo4j and data directories
# (edit as appropriate for local environment)
echo "Creating symbolic links..."
if [ ! -f neo4j ]; then
    echo "    Linking neo4j folder..."
    ln -s /dtmp/`whoami`/neo4j neo4j
else
    echo "    Using existing neo4j folder..."
fi
if [ ! -f data ]; then
    echo "    Linking data folder..."
    ln -s /dtmp/`whoami`/data data
else
    echo "    Using existing data folder..."
fi

# Change permissions to make things easy
echo "Setting permissions..."
chmod +x start_docker.sh
chmod 777 -R notebooks
chmod 777 -R neo4j
chmod 777 -R data
chmod 777 requirements.txt

# Export some environmental variables for Docker
echo "Exporting environmental variables..."
if [[ $HOSTNAME = *"matlaber"* ]]; then
  echo "    Changing groupid to mlusers and symlinking folders"
  chown -R `id -u`:mlusers neo4j
  chown -R `id -u`:mlusers data
fi
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

#  Launch Docker
echo "Launching Docker..."
echo "    Building services..."
docker-compose build
echo "    (Re)building and launching services..."
docker-compose up
echo "Done."

