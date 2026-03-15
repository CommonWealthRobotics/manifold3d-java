missing_vars=()
[[ -z "${CLOJARS_USERNAME}" ]] && missing_vars+=("CLOJARS_USERNAME")
[[ -z "${CLOJARS_TOKEN}" ]] && missing_vars+=("CLOJARS_TOKEN")
[[ -z "${VERSION}" ]] && missing_vars+=("VERSION")
[[ -z "${JAVA_HOME}" ]] && missing_vars+=("JAVA_HOME")

if [[ ${#missing_vars[@]} -gt 0 ]]; then
  echo "Error: The following required environment variables are not set: ${missing_vars[*]}"
  echo "Create them at https://clojars.org/profile"
  exit 1
fi


set -e
mvn versions:set -DnewVersion=$VERSION --file bindings/java/pom.xml
cd bindings/java/
mvn package -Dos.classifier=linux-x86_64
cd ../../
set +e
mv bindings/java/target/manifold3d-*.jar bindings/java/target/manifold3d-$VERSION.jar
set -e
mvn deploy:deploy-file \
  -DpomFile=bindings/java/pom.xml \
  -DrepositoryId=clojars \
  -Durl=https://clojars.org/repo \
  -DgroupId=com.github.madhephaestus \
  -DartifactId=manifold3d \
  -Dversion=$VERSION \
  -Dpackaging=jar \
  -Dfile=bindings/java/target/manifold3d-$VERSION.jar \
  --settings=bindings/java/settings.xml