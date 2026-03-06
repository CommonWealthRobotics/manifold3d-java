#!/bin/bash

echo "Java Home set to: "$JAVA_HOME
printenv JAVA_HOME

missing_vars=()
[[ -z "${JAVA_HOME}" ]] && missing_vars+=("JAVA_HOME")

if [[ ${#missing_vars[@]} -gt 0 ]]; then
  echo "Error: The following required environment variables are not set: ${missing_vars[*]}"
  exit 1
fi

VERSION=$(cat ./bindings/java/version.txt)

cd bindings/java
set -e
mvn versions:set -DnewVersion=$(cat version.txt) --file pom.xml
mvn package -Dos.classifier=linux-x86_64
mv ./target/manifold3d-$VERSION-linux-x86_64.jar ./target/manifold3d-$VERSION.jar

