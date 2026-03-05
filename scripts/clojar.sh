#!/bin/bash

missing_vars=()
[[ -z "${CLOJARS_USERNAME}" ]] && missing_vars+=("CLOJARS_USERNAME")
[[ -z "${CLOJARS_TOKEN}" ]] && missing_vars+=("CLOJARS_TOKEN")

if [[ ${#missing_vars[@]} -gt 0 ]]; then
  echo "Error: The following required environment variables are not set: ${missing_vars[*]}"
  echo "Create them at https://clojars.org/profile"
  exit 1
fi

VERSION=$(cat ./bindings/java/version.txt)
linux_jar_file=./bindings/java/target/manifold3d-$VERSION.jar
mv ./bindings/java/target/manifold3d-*-linux-x86_64.jar $linux_jar_file
ls -la $linux_jar_file  # verify it exists before deploying
mvn deploy:deploy-file \
  -DpomFile=bindings/java/pom.xml \
  -DrepositoryId=clojars \
  -Durl=https://clojars.org/repo \
  -DgroupId=com.github.madhephaestus \
  -DartifactId=manifold3d \
  -Dversion=$VERSION \
  -Dpackaging=jar \
  -Dfile=$linux_jar_file \
  --settings=bindings/java/settings.xml