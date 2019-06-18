#!/bin/bash

# Colors
RED=`tput setaf 1`
BLUE=`tput setaf 4`
GREEN=`tput setaf 2`
MAGENTA=`tput setaf 6`
BLACK_BG=`tput setab 0`
RESET=`tput sgr0`

# Create correct symbolic links to neo4j and data directories
# (edit as appropriate for local environment)
#echo "${MAGENTA}Creating symbolic links...${RESET}"
#if ! [ -L neo4j ]; then
#    echo "    ${GREEN}Linking neo4j folder...{$RESET}"
#    ln -s "/dtmp/$(whoami)/neo4j" ./neo4j
#    echo "    ${GREEN}neo4j -> /dtmp/`whoami`/neo4j"
#else
#    echo "    ${GREEN}Using existing neo4j symlink..."
#fi
#if ! [ -L data ]; then
#    echo "    ${GREEN}Linking data folder...${RESET}"
#    ln -s "/dtmp/$(whoami)/data" ./data
#    echo "    data -> /dtmp/`whoami`/data"
#else
#    echo "    ${GREEN}Using existing data symlink...${RESET}"
#fi

# Change permissions to make things easy
echo "${MAGENTA}Setting permissions...${RESET}"
chmod +x start_docker.sh
chmod 777 -R notebooks
chmod 777 -R neo4j
chmod 777 -R data
chmod 777 requirements.txt

# Export some environmental variables for Docker
echo "${MAGENTA}Exporting environmental variables...${RESET}"

#if [[ $HOSTNAME = *"matlaber"* ]]; then
#    echo "    ${GREEN}Changing groupid to mlusers and symlinking folders"
#    chown -R `id -u`:mlusers neo4j
#    chown -R `id -u`:mlusers data
#fi

export UID=$(id -u) &> /dev/null
echo "    ${GREEN}UID=$UID"

export GID=$(id -g)
echo "    ${GREEN}GID=$GID"

export HOSTNAME=$(hostname)
echo "    ${GREEN}HOSTNAME=$HOSTNAME"

export DNSDOMAINNAME=$(dnsdomainname)
echo "    ${GREEN}DNSDOMAINNAME=$DNSDOMAINNAME"

export FQDN=$(hostname -A | cut -d' ' -f1)
echo "    ${GREEN}FQDN=$FQDN"

# Remove authentication locks, if they exist
echo "${MAGENTA}Looking for database locks...${RESET}"
if [ -f ./neo4j/neo4j-coauthor/data/dbms/auth ]; then
    echo "   ${GREEN}Removing neo4j-coauthor database authentication lock..."
    rm ./neo4j/neo4j-coauthor/data/dbms/auth
fi
if [ -f ./neo4j/neo4j-quanta/data/dbms/auth ]; then
    echo "   ${GREEN}Removing neo4j-quanta database authentication lock..."
    rm ./neo4j/neo4j-quanta/data/dbms/auth
fi

# Select Neo4j database to launch
if [ "$1" == "dev" ] || [ "$1" == "development" ]
then
    echo "   ${GREEN}Selected development database..."
    export DBNAME="dev"
elif [ "$1" == "test" ] || [ "$1" == "testing" ]
then
    echo "   ${GREEN}Selected testing database..."
    export DBNAME="test"
elif [ "$1" == "stage" ] || [ "$1" == "staging" ]
then
    echo "   ${GREEN}Selected staging database..."
    export DBNAME="stage"
else
	echo "${RED}ERROR: No database version specified."
    exit 1
fi

# Export Docker Compose-related environmental variables
echo "   ${GREEN}Using ${DBNAME}...${BLUE}"
export COMPOSE_PROJECT_NAME=$DBNAME
export COMPOSE_FILE=docker-compose.yml

# Launch Docker
echo "${MAGENTA}Launching Docker...${RESET}"
echo "   ${GREEN}Building containers...${BLUE}"
docker-compose build
echo "   ${GREEN}Launching containers...${BLUE}"
docker-compose up


