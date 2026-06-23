#!/bin/bash

set -e

if [ ! -d clipper2 ]; then
  git clone https://github.com/AngusJohnson/Clipper2.git clipper2
fi

cd clipper2
git fetch --all --tags
git checkout 46f639177fe418f9689e8ddb74f08a870c71f5b4
cd ..
if [ ! -d nanobind ]; then
  git clone https://github.com/wjakob/nanobind.git nanobind
fi

cd nanobind
git fetch --all --tags
git checkout v2.12.0
cd ..
if [ ! -d tbb ]; then
  git clone https://github.com/oneapi-src/oneTBB.git tbb
fi

cd tbb
git fetch --all --tags
git checkout v2022.3.0
cd ..
if [ ! -d gtest ]; then
  git clone https://github.com/google/googletest.git gtest
fi

cd gtest
git fetch --all --tags
git checkout v1.17.0
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
make
sudo make install
cd ../..

#rm -rf build
mkdir build
cd build
cmake \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DMANIFOLD_STRICT=ON \
  -DMANIFOLD_USE_BUILTIN_TBB=ON \
  -DMANIFOLD_DEBUG=OFF \
  -DMANIFOLD_ASSERT=OFF \
  -DMANIFOLD_CROSS_SECTION=ON \
  -DMANIFOLD_EXPORT=OFF \
  -DMANIFOLD_PAR=ON \
  -DFETCHCONTENT_SOURCE_DIR_TBB=../tbb \
  -DFETCHCONTENT_SOURCE_DIR_CLIPPER2=../clipper2 \
  -DFETCHCONTENT_SOURCE_DIR_NANOBIND=../nanobind \
  -DFETCHCONTENT_SOURCE_DIR_GOOGLETEST=../gtest \
  -A x64 -B .
cmake --build . --target ALL_BUILD --config Release
cd ..
mkdir -p ./bindings/java/src/main/resources/manifold3d/natives/win-x86_64/
rm -rf ./bindings/java/src/main/resources/manifold3d/natives/win-x86_64/*
cp ./build/lib/Release/manifold.dll ./bindings/java/src/main/resources/manifold3d/natives/win-x86_64/
cp ./build/lib/Release/manifoldc.dll ./bindings/java/src/main/resources/manifold3d/natives/win-x86_64/
mvn test --file bindings/java/pom.xml --no-transfer-progress
