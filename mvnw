#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
# ----------------------------------------------------------------------------

# Maven Wrapper script for POSIX shells
# Requires: Java 17+, curl or wget

set -e

MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  DISTRIBUTION_URL=$(grep -m 1 "^distributionUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_HOME="${HOME}/.m2/wrapper/dists/$(basename "$DISTRIBUTION_URL" -bin.zip)"

if [ ! -d "$MAVEN_HOME" ]; then
  echo "Downloading Maven from $DISTRIBUTION_URL"
  mkdir -p "$MAVEN_HOME"
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL -o "/tmp/maven.zip" "$DISTRIBUTION_URL"
  else
    wget -q -O "/tmp/maven.zip" "$DISTRIBUTION_URL"
  fi
  unzip -q "/tmp/maven.zip" -d "$MAVEN_HOME"
  rm "/tmp/maven.zip"
fi

MVN_EXEC=$(find "$MAVEN_HOME" -name "mvn" -type f | head -1)
exec "$MVN_EXEC" "$@"

