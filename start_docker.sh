#!/bin/bash

export UID=$UID
export GID=$GID

echo "Set env vars UID=$UID and GID=$GID for volume sharing."
docker-compose up
