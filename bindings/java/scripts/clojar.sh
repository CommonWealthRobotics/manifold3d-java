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

echo "Clojar Java $JAVA_HOME"
echo "Clojar Version: $1"

set -e
mvn versions:set -DnewVersion=$1 --file bindings/java/pom.xml
cd bindings/java/
mvn package -Dos.classifier=linux-x86_64
cd ../../
set +e
#mv bindings/java/target/manifold3d-*.jar bindings/java/target/manifold3d-$1.jar
set -e
mvn deploy:deploy-file \
  -DpomFile=bindings/java/pom.xml \
  -DrepositoryId=clojars \
  -Durl=https://clojars.org/repo \
  -Dfile=bindings/java/target/manifold3d-$1.jar \
  --settings=bindings/java/settings.xml