mkdir build
cd build
cmake -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DMANIFOLD_DEBUG=ON -DMANIFOLD_EXPORT=ON -DMANIFOLD_PAR=OFF -DCMAKE_POLICY_VERSION_MINIMUM=3.5 .. && make
cd ..
cd bindings/java

mvn versions:set -DnewVersion=$(cat version.txt) --file pom.xml
mvn package -Dos.classifier=linux-x86_64
