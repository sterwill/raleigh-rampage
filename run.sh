#!/bin/sh
#
# Build with "mvn package" before using this script.

java -Xmx1000M -cp target/raleigh-rampage-0.0.1-SNAPSHOT-jar-with-dependencies.jar com.tinfig.rr.Main -d 1 -d 2 -d 3
