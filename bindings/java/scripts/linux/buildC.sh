#!/bin/bash

VERSION=$(git describe --tags --abbrev=0)
#rm -rf build
mkdir build
cd build
set -e
cmake -DMANIFOLD_CROSS_SECTION=ON -DMANIFOLD_USE_BUILTIN_CLIPPER2=ON -DCMAKE_BUILD_TYPE=Release -DASSIMP_ENABLE=ON -DBUILD_SHARED_LIBS=OFF -DMANIFOLD_DEBUG=ON -DMANIFOLD_EXPORT=ON -DMANIFOLD_PAR=ON -DMANIFOLD_USE_BUILTIN_TBB=ON -DCMAKE_POLICY_VERSION_MINIMUM=3.5 ..
make
cd ..
echo "Build Done! Now copying the binaries into the Java Build..."
echo "Current directory is $PWD"
ls -al .
ls -al ./build/src/*
cp ./build/src/libmanifold.so ./bindings/java/src/main/resources/manifold3d/natives/linux-x86_64/
cp ./build/bindings/c/libmanifoldc.so ./bindings/java/src/main/resources/manifold3d/natives/linux-x86_64/
