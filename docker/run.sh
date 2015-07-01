#!/bin/bash

if [ $LOGENTRIES_TOKEN ]; then
    JAVA_OPTS="$JAVA_OPTS -Dlogentries.token=$LOGENTRIES_TOKEN";
fi

if [ -f "log4j.xml" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dlog4j.configuration=file:/home/reddcrawl/log4j.xml";
fi

java $JAVA_OPTS -jar reddcrawl-1.0-SNAPSHOT.jar $@
