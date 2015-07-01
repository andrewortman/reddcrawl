#!/bin/bash

DOCKER_ID=$(uuidgen | awk '{print tolower($0)}')
docker build -t $DOCKER_ID .
docker tag -f $DOCKER_ID andrewortman/reddcrawl
docker push andrewortman/reddcrawl
