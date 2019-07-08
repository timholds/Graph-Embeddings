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
#chmod 777 -R neo4j
#chmod 777 -R data
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


# Select Neo4j database to launch
echo "${MAGENTA}Setting database to launch...${RESET}"

# Select base directory for database
if [ "$2" == "dtmp" ]; then
    export DBBASE="/dtmp/jww/neo4j"
else
    export DBBASE="./neo4j"
fi
echo "   ${GREEN}Setting base directory to ${DBBASE}...${BLUE}"

# Select the specific database name
export DBNAME="$1"
if [ "$DBNAME" != "" ] && [ -d "${DBBASE}/${DBNAME}" ]; then
    echo "   ${GREEN}Using database ${DBNAME} at ${DBBASE}/${DBNAME}...${BLUE}"
else
    echo "   ${RED}ERROR: Specified database is not valid. Exiting."
    exit 1
fi

# Remove authentication locks, if they exist
echo "${MAGENTA}Looking for database locks...${RESET}"
if [ -f ${DBBASE}/${DBNAME}/data/dbms/auth ]; then
    echo "   ${GREEN}Removing database authentication lock..."
    rm  ${DBBASE}/${DBNAME}/data/dbms/auth
fi

# Export Docker Compose-related environmental variables
export COMPOSE_PROJECT_NAME=$DBNAME
export COMPOSE_FILE=docker-compose.yml

## Launch Docker
echo "${MAGENTA}Launching Docker...${RESET}"
echo "   ${GREEN}Building containers...${BLUE}"
docker-compose build
echo "   ${GREEN}Launching containers...${BLUE}"
docker-compose up


