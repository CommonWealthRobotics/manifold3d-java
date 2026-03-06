#!/bin/bash

VERSION=$(cat ./bindings/java/version.txt)
#rm -rf build
mkdir build
cd build
set -e
cmake -DCMAKE_BUILD_TYPE=Release -DASSIMP_ENABLE=ON -DBUILD_SHARED_LIBS=ON -DMANIFOLD_DEBUG=ON -DMANIFOLD_EXPORT=ON -DMANIFOLD_PAR=ON -DMANIFOLD_USE_BUILTIN_TBB=ON -DCMAKE_POLICY_VERSION_MINIMUM=3.5 ..
make
cd ..
