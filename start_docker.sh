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
export UID=$(id -u)
echo "    UID=$UID"
export GID=$(id -g)
echo "    GID=$GID"
export HOSTNAME=$(hostname)
echo "    HOSTNAME=$HOSTNAME"
export DNSDOMAINNAME=$(dnsdomainname)
echo "    DNSDOMAINNAME=$DNSDOMAINNAME"


# Remove authentication locks, if they exist
echo "Looking for database locks..."
if [ -f ./neo4j/neo4j-coauthor/data/dbms/auth ]; then
    echo "   Removing neo4j-coauthor database authentication lock..."
    rm ./neo4j/neo4j-coauthor/data/dbms/auth
fi
if [ -f ./neo4j/neo4j-quanta/data/dbms/auth ]; then
    echo "   Removing neo4j-quanta database authentication lock..."
    rm ./neo4j/neo4j-quanta/data/dbms/auth
fi

# Launch Docker
echo "Launching Docker..."
echo "   Building services..."
docker-compose build
echo "   (Re)building and launching services..."
docker-compose up
echo "Done."


