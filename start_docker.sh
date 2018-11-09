#!/bin/bash

# Create correct symbolic links to neo4j and data directories
# (edit as appropriate for local environment)
echo "Creating symbolic links..."
if [ ! -L ./neo4j ]; then
    echo "    Linking neo4j folder..."
    ln -s /dtmp/`whoami`/neo4j ./neo4j
    echo "    neo4j -> /dtmp/`whoami`/neo4j"
else
    echo "    Using existing neo4j symlink..."
fi
if [ ! -L ./data ]; then
    echo "    Linking data folder..."
    ln -s /dtmp/`whoami`/data ./data
    echo "    data -> /dtmp/`whoami`/data"
else
    echo "    Using existing data symlink..."
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
echo "    UID=$UID"
export UID=$(id -u)
echo "    GID=$GID"
export GID=$(id -g)
export HOSTNAME=$(hostname)
echo "    HOSTNAME=$HOSTNAME"
export DNSDOMAINNAME=$(dnsdomainname)
echo "    DNSDOMAINNAME=$DNSDOMAINNAME"


#  Launch Docker
echo "Launching Docker..."
if [ -f ./neo4j/data/dbms/auth ]; then
    echo "   Removing neo4j database authentication lock..."
    rm ./neo4j/data/dbms/auth
fi
echo "   Building services..."
docker-compose build
echo "   (Re)building and launching services..."
docker-compose up

# Rename containers
#echo "Renaming Docker containers..."
#echo "    Renaming SciPy Notebook container..."
#docker rename scipy-notebook-container scipy-notebook-container
#echo "    Renaming Neo4j container..."
#docker rename neo4j-container neo4j-container

echo "Done."


