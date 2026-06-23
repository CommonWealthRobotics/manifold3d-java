#!/bin/bash

#rm -rf build
mkdir build
cd build
set -e
cmake -DMANIFOLD_CROSS_SECTION=ON -DMANIFOLD_USE_BUILTIN_CLIPPER2=ON -DCMAKE_BUILD_TYPE=Release -DASSIMP_ENABLE=ON -DBUILD_SHARED_LIBS=ON -DMANIFOLD_DEBUG=ON -DMANIFOLD_EXPORT=ON -DMANIFOLD_PAR=ON -DMANIFOLD_USE_BUILTIN_TBB=ON -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_BUILD_RPATH='$ORIGIN' ..
make
cd ..
echo "Build Done! Now copying the binaries into the Java Build..."
echo "Current directory is $PWD"
ls -al .
ls -al ./build/src/*
mkdir -p ./bindings/java/src/main/resources/manifold3d/natives/linux-arm64/
cp ./build/src/libmanifold.so ./bindings/java/src/main/resources/manifold3d/natives/linux-arm64/
ln -sf libmanifold.so ./bindings/java/src/main/resources/manifold3d/natives/linux-arm64/libmanifold.so.3
ln -sf libmanifold.so.3 ./bindings/java/src/main/resources/manifold3d/natives/linux-arm64/libmanifold.so.3.5.1
cp ./build/bindings/c/libmanifoldc.so ./bindings/java/src/main/resources/manifold3d/natives/linux-arm64/
ln -sf libmanifoldc.so ./bindings/java/src/main/resources/manifold3d/natives/linux-arm64/libmanifoldc.so.3
ln -sf libmanifoldc.so.3 ./bindings/java/src/main/resources/manifold3d/natives/linux-arm64/libmanifoldc.so.3.5.1
