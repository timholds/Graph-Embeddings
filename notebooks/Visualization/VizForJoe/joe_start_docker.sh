#!/bin/bash

echo "Setting permissions..."
chmod +x joe_start_docker.sh
chmod 777 -R "$PWD/../../../"
chmod 777 requirements.txt

# Export some environmental variables for Docker
if [[ $HOSTNAME = *"matlaber"* ]]; then
    echo "    Changing groupid to mlusers and symlinking folders"
    chown -R `id -u`:mlusers "$PWD/.."
fi
export UID=$(id -u)
echo "    UID=$UID"
export GID=$(id -g)
echo "    GID=$GID"
export HOSTNAME=$(hostname)
echo "    HOSTNAME=$HOSTNAME"
export DNSDOMAINNAME=$(dnsdomainname)
echo "    DNSDOMAINNAME=$DNSDOMAINNAME"

# Launch Docker
echo "Launching Docker..."
echo "   Building services..."
docker-compose build
echo "   (Re)building and launching services..."
docker-compose up
echo "Done."


