export JAVA_HOME=$HOME/bin/java21/
rm -rf build
mkdir build
cd build
cmake -DCMAKE_BUILD_TYPE=Release -DASSIMP_ENABLE=ON -DBUILD_SHARED_LIBS=ON -DMANIFOLD_DEBUG=ON -DMANIFOLD_EXPORT=ON -DMANIFOLD_PAR=NONE -DCMAKE_POLICY_VERSION_MINIMUM=3.5 .. && make
cd ..
cd bindings/java

mvn versions:set -DnewVersion=$(cat version.txt) --file pom.xml
mvn package -Dos.classifier=all
