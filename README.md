[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.cartesiantheatrics/manifold3d.svg)](https://clojars.org/org.clojars.cartesiantheatrics/manifold3d)
[![codecov](https://codecov.io/github/elalish/manifold/branch/master/graph/badge.svg?token=IIA8G5HVS7)](https://codecov.io/github/elalish/manifold)
[![PyPI version](https://badge.fury.io/py/manifold3d.svg)](https://badge.fury.io/py/manifold3d)
[![npm version](https://badge.fury.io/js/manifold-3d.svg)](https://badge.fury.io/js/manifold-3d)
[![twitter](https://img.shields.io/twitter/follow/manifoldcad?style=social&logo=twitter)](https://twitter.com/intent/follow?screen_name=manifoldcad)

This fork Maintains java/JNI bindings to [Manifold](https://github.com/elalish/manifold). It supports nearly all the features, plus extends Manifold with support for polyhedrons, lofts, and text. It has builds for linux and mac (experimental builds for windows have been dropped).
[**C++ Documentation**](https://manifoldcad.org/docs/html/classmanifold_1_1_manifold.html) | [**ManifoldCAD User Guide**](https://manifoldcad.org/docs/jsuser/) | [**JS/TS/WASM API**](https://manifoldcad.org/docs/jsapi/) | [**Algorithm Documentation**](https://github.com/elalish/manifold/wiki/Manifold-Library) | [**Blog Posts**](https://elalish.blogspot.com/search/label/Manifold) | [**Web Examples**](https://manifoldcad.org/model-viewer.html)

Consider using the [Clojure library](https://github.com/SovereignShop/clj-manifold3d) for a great interactive development environment for solid modeling. 

| | | |
| --- | --- | --- |
| [OpenSCAD](https://openscad.org/) | [Blender](https://www.blender.org/) | [IFCjs](https://ifcjs.github.io/info/) |
| [Nomad Sculpt](https://apps.apple.com/us/app/id1519508653?mt=8&platform=ipad) | [Grid.Space](https://grid.space/) | [badcad](https://github.com/wrongbad/badcad) |
| [Godot Engine](https://godotengine.org/) | [OCADml](https://github.com/OCADml/OManifold) | [Flitter](https://flitter.readthedocs.io/en/latest/) |
| [BRL-CAD](https://brlcad.org/) | [PolygonJS](https://polygonjs.com/) | [Spherene](https://spherene.ch/) |
| [Babylon.js](https://doc.babylonjs.com/features/featuresDeepDive/mesh/mergeMeshes#merging-meshes-with-constructive-solid-geometry) | [trimesh](https://trimesh.org/) | [Gypsum](https://github.com/playkostudios/gypsum) |
| [Valence 3D](https://apps.apple.com/us/app/valence-3d/id6450967410?mt=8&platform=ipad) | [bitbybit.dev](https://bitbybit.dev) | [PythonOpenSCAD](https://github.com/owebeeone/pythonopenscad) |
| [Conversation](https://james-bern.github.io/conversation.html) | [AnchorSCAD](https://github.com/owebeeone/anchorscad-core) | [Dactyl Web Configurator](https://github.com/rianadon/dactyl-configurator) |
| [Arcol](https://arcol.io) | [Bento3D](https://bento3d.design) | [SKÅPA](https://skapa.build) |
| [Cadova](https://github.com/tomasf/Cadova) | [BREP.io](https://github.com/mmiscool/BREP)  | [Otterplans](https://otterplans.com) |
| [Bracket Engineer](https://bracket.engineer) | [Nodillo](https://nodillo3d.com) | |
| Language | Packager | Name | Maintenance |
| --- | --- | --- | --- |
| C | N/A | N/A | internal |
| C++ | vcpkg | [manifold](https://github.com/microsoft/vcpkg/tree/master/ports/manifold) | external |
| TS/JS | npm | [manifold-3d](https://www.npmjs.com/package/manifold-3d) | internal |
| Python | PyPI | [manifold3d](https://pypi.org/project/manifold3d/) | internal |
| Java | N/A | [manifold](https://github.com/CommonWealthRobotics/manifold3d-java) | external |
| Clojure | N/A | [clj-manifold3d](https://github.com/SovereignShop/clj-manifold3d) | external |
| C# | NuGet | [ManifoldNET](https://www.nuget.org/packages/ManifoldNET) | external |
| Julia | Packages | [ManifoldBindings.jl](https://juliapackages.com/p/manifoldbindings) | external |
| OCaml | N/A | [OManifold](https://ocadml.github.io/OManifold/OManifold/index.html) | external |
| Swift | SPM | [Manifold-Swift](https://github.com/tomasf/manifold-swift) | external |

## Example

``` java
package manifold3d;

import manifold3d.Manifold;
import manifold3d.pub.DoubleMesh;
import manifold3d.glm.DoubleVec3;
import manifold3d.manifold.MeshIO;
import manifold3d.manifold.ExportOptions;

public class ManifoldExample {

    public static void main(String[] args) {
        Manifold sphere = Manifold.Sphere(10.0f, 20);
        Manifold cube = Manifold.Cube(new DoubleVec3(15.0, 15.0, 15.0), false);
        Manifold cylinder = Manifold.Cylinder(3, 30.0f, 30.0f, 0, false)
                .translateX(20).translateY(20).translateZ(-3.0);

        // Perform Boolean operations
        Manifold diff = cube.subtract(sphere);
        Manifold intersection = cube.intersect(sphere);
        Manifold union = cube.add(sphere);

        ExportOptions opts = new ExportOptions();
        opts.faceted(false);

        MeshIO.ExportMesh("CubeMinusSphere.stl", diff.getMesh(), opts);
        MeshIO.ExportMesh("CubeIntersectSphere.glb", intersection.getMesh(), opts);
        MeshIO.ExportMesh("CubeUnionSphere.obj", union.getMesh(), opts);

        Manifold hull = cylinder.convexHull(cube.translateZ(100.0));
        MeshIO.ExportMesh("hull.glb", hull.getMesh(), opts);
    }
}
```


## Installation

You need to include a classifier for your platform and desired backend. For linux, a TBB (Threading Building Blocks) backend is available with classifier `linux-TBB-x86_64`. The most battle tested version is the single-threaded implementation: `linux-x86_64` (NOTE: CUDA backends were removed in 1.0.73). 

Similarly for mac, `mac-TBB-x86_64` and `mac-x86_64` classifiers are available.

The Manifold .so libs are included in the bindings jar. You'll also need to have libassimp installed on your system:

``` sh
;; Ubuntu
sudo apt install libassimp-dev
;; Mac
brew install pkg-config assimp
```

## Manifold Frontend Sandboxes

[ManifoldCAD.org]: https://manifoldcad.org
[Python Colab Example]: https://colab.research.google.com/drive/1VxrFYHPSHZgUbl9TeWzCeovlpXrPQ5J5?usp=sharing

### [ManifoldCAD.org]

*Note for Firefox users: If you find the editor is stuck on **Loading...**, setting
`dom.workers.modules.enabled: true` in your `about:config`, as mentioned in
[issue#328](https://github.com/elalish/manifold/issues/328#issuecomment-1473847102)
may solve the problem.*

### [Python Colab Example](https://colab.research.google.com/drive/1VxrFYHPSHZgUbl9TeWzCeovlpXrPQ5J5?usp=sharing)
If you like OpenSCAD / JSCAD, you might also like [ManifoldCAD][ManifoldCAD.org] - our own solid modelling web app where you script in JS/TS. This uses our npm package, [manifold-3d](https://www.npmjs.com/package/manifold-3d), built via WASM. It's not quite as fast as our raw C++, but it's hard to beat for interoperability.

### [Python Colab Example]

If you prefer Python to JS/TS, make your own copy of [the notebook][Python Colab Example]. It demonstrates interop between our [`manifold3d`](https://pypi.org/project/manifold3d/) PyPI library and the popular [`trimesh`](https://pypi.org/project/trimesh/) library, including showing the interactive model right in the notebook and saving 3D model output.

![A metallic Menger sponge](https://elalish.github.io/manifold/samples/models/mengerSponge3.webp "A metallic Menger sponge")

# Manifold

[**API Documentation**](https://elalish.github.io/manifold/docs/html/topics.html) | [**Algorithm Documentation**](https://github.com/elalish/manifold/wiki/Manifold-Library) | [**Blog Posts**](https://elalish.blogspot.com/search/label/Manifold) | [**Web Examples**](https://elalish.github.io/manifold/model-viewer.html)

[Manifold](https://github.com/elalish/manifold) is a geometry library dedicated to creating and operating on manifold triangle meshes. A [manifold mesh](https://github.com/elalish/manifold/wiki/Manifold-Library#manifoldness) is a mesh that represents a solid object, and so is very important in manufacturing, CAD, structural analysis, etc. Further information can be found on the [wiki](https://github.com/elalish/manifold/wiki/Manifold-Library).

This is a modern C++ library that Github's CI verifies builds and runs on a variety of platforms. Additionally, we build bindings for JavaScript ([manifold-3d](https://www.npmjs.com/package/manifold-3d) on npm), Python ([manifold3d](https://pypi.org/project/manifold3d/)), and C to make this library more portable and easy to use.

System Dependencies (note that we will automatically download the dependency if there is no such package on the system):
- [`GLM`](https://github.com/g-truc/glm/): A compact header-only vector library.
- [`Thrust`](https://github.com/NVIDIA/thrust): NVIDIA's parallel algorithms library (basically a superset of C++17 std::parallel_algorithms)
- [`tbb`](https://github.com/oneapi-src/oneTBB/): Intel's thread building blocks library. (only when `MANIFOLD_PAR=TBB` is enabled)
- [`gtest`](https://github.com/google/googletest/): Google test library (only when test is enabled, i.e. `MANIFOLD_TEST=ON`)

Other dependencies:
- [`Clipper2`](https://github.com/AngusJohnson/Clipper2): provides our 2D subsystem
- [`quickhull`](https://github.com/akuukka/quickhull): 3D convex hull algorithm.

## What's here

This library is fast with guaranteed manifold output. As such you need manifold meshes as input, which this library can create using constructors inspired by the OpenSCAD API, as well as more advanced features like smoothing and signed-distance function (SDF) level sets. You can also pass in your own mesh data, but you'll get an error status if the imported mesh isn't manifold. Various automated repair tools exist online for fixing non manifold models, usually for 3D printing.

The most significant contribution here is a guaranteed-manifold [mesh Boolean](https://github.com/elalish/manifold/wiki/Manifold-Library#mesh-boolean) algorithm, which I believe is the first of its kind. If you know of another, please open a discussion - a mesh Boolean algorithm robust to edge cases has been an open problem for many years. Likewise, if the Boolean here ever fails you, please submit an issue! This Boolean forms the basis of a CAD kernel, as it allows simple shapes to be combined into more complex ones.

To aid in speed, this library makes extensive use of parallelization, generally through Nvidia's Thrust library. You can switch between the TBB, and serial C++ backends by setting a CMake flag. Not everything is so parallelizable, for instance a [polygon triangulation](https://github.com/elalish/manifold/wiki/Manifold-Library#polygon-triangulation) algorithm is included which is serial. Even if compiled with parallel backend, the code will still fall back to the serial version of the algorithms if the problem size is small. The WASM build is serial-only for now, but still fast.

> Note: OMP and CUDA backends are now removed

Look in the [samples](https://github.com/elalish/manifold/tree/master/samples) directory for examples of how to use this library to make interesting 3D models. You may notice that some of these examples bare a certain resemblance to my OpenSCAD designs on [Thingiverse](https://www.thingiverse.com/emmett), which is no accident. Much as I love OpenSCAD, my library is dramatically faster and the code is more flexible.


### Dependencies

Manifold no longer has **any** required dependencies! However, we do have several optional dependencies, of which the first two are strongly encouraged:
| Name | CMake Flag | Provides |
| --- | --- | --- |
| [`TBB`](https://github.com/oneapi-src/oneTBB/) |`MANIFOLD_PAR=ON` | Parallel acceleration |
| [`Clipper2`](https://github.com/AngusJohnson/Clipper2) | `MANIFOLD_CROSS_SECTION=ON` | 2D: [`CrossSection`](https://manifoldcad.org/docs/html/classmanifold_1_1_cross_section.html) |
| [`Nanobind`](https://github.com/wjakob/nanobind) | `MANIFOLD_PYBIND=ON` | Python bindings |
| [`Emscripten`](https://github.com/emscripten-core/emscripten) | `MANIFOLD_JSBIND=ON` | JS bindings via WASM |
| [`GTest`](https://github.com/google/googletest/) | `MANIFOLD_TEST=ON` | Testing framework |
| [`Assimp`](https://github.com/assimp/assimp) | `ASSIMP_ENABLE=ON` | Utilities in `extras` |
| [`Tracy`](https://github.com/wolfpld/tracy) | `TRACY_ENABLE=ON` | Performance analysis |


### 3D Formats

Please avoid saving to STL files! They are lossy and inefficient - when saving a manifold mesh to STL there is no guarantee that the re-imported mesh will still be manifold, as the topology is lost. Please consider using [3MF](https://3mf.io/) instead, as this format was designed from the beginning for manifold meshes representing solid objects. 

If you use vertex properties for things like interpolated normals or texture UV coordinates, [glTF](https://www.khronos.org/Gltf) is recommended, specifically using the [`EXT_mesh_manifold`](https://github.com/KhronosGroup/glTF/blob/main/extensions/2.0/Vendor/EXT_mesh_manifold/README.md) extension. This allows for the lossless and efficient transmission of manifoldness even with property boundaries. Try our [make-manifold](https://manifoldcad.org/make-manifold) page to add this extension to your existing glTF/GLB models. 

Manifold provides high precision OBJ file IO, but it is limited in functionality and is primarily to aid in testing. If you are using our npm module, we have a much more capable [gltf-io.ts](https://github.com/elalish/manifold/blob/master/bindings/wasm/examples/gltf-io.ts) you can use instead. For other languages we strongly recommend using existing packages that focus on 3D file I/O, e.g. [trimesh](https://trimesh.org/) for Python, particularly when using vertex properties or materials.
Example for integrating with [Assimp](https://github.com/assimp/assimp) is in `extras/meshIO.cpp`, which is used by files such as `extras/convert_file.cpp`.

## Building

Only CMake, a C++ compiler, and Python are required to be installed and set up to build this library (it has been tested with GCC, LLVM, MSVC). However, a variety of optional dependencies can bring in more functionality, see below.

Build and test (Ubuntu or similar):
```
git clone --recurse-submodules https://github.com/elalish/manifold.git
cd manifold
mkdir build
cd build
cmake -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON .. && make
make test
```

CMake flags (usage e.g. `-DMANIFOLD_DEBUG=ON`):
- `MANIFOLD_JSBIND=[OFF, <ON>]`: Build js binding when using emscripten.
- `MANIFOLD_CBIND=[<OFF>, ON]`: Build C FFI binding.
- `MANIFOLD_PYBIND=[OFF, <ON>]`: Build python binding.
- `MANIFOLD_PAR=[<NONE>, TBB]`: Provides multi-thread parallelization, requires `libtbb-dev` if `TBB` backend is selected.
- `MANIFOLD_EXPORT=[<OFF>, ON]`: Enables GLB export of 3D models from the tests, requires `libassimp-dev`.
- `MANIFOLD_DEBUG=[<OFF>, ON]`: Enables internal assertions and exceptions.
- `MANIFOLD_TEST=[OFF, <ON>]`: Build unittests.
- `TRACY_ENABLE=[<OFF>, ON]`: Enable integration with tracy profiler. 
  See profiling section below.
- `BUILD_TEST_CGAL=[<OFF>, ON]`: Builds a CGAL-based performance [comparison](https://github.com/elalish/manifold/tree/master/extras), requires `libcgal-dev`.

Offline building:
- `FETCHCONTENT_SOURCE_DIR_GLM`: path to glm source.
- `FETCHCONTENT_SOURCE_DIR_GOOGLETEST`: path to googletest source.
- `FETCHCONTENT_SOURCE_DIR_THRUST`: path to NVIDIA thrust source.
- `MANIFOLD_PYBIND=[OFF, <ON>]`: Build python binding, requires `nanobind`.
- `MANIFOLD_PAR=[<OFF>, ON]`: Enables multi-thread parallelization, requires `tbb`.
- `MANIFOLD_CROSS_SECTION=[OFF, <ON>]`: Build CrossSection for 2D support (needed by language bindings), requires `Clipper2`.
- `MANIFOLD_DEBUG=[<OFF>, ON]`: Enables exceptions, timing, verbosity, OBJ test dumps. Has almost no effect on its own, but enables further runtime parameters to dump various outputs.
- `MANIFOLD_ASSERT=[<OFF>, ON]`: Enables internal assertions. This incurs around 20% runtime overhead. Requires MANIFOLD_DEBUG to work.
- `MANIFOLD_TEST=[OFF, <ON>]`: Build unit tests, requires `GTest`.
- `TRACY_ENABLE=[<OFF>, ON]`: Enable integration with tracy profiler. 
  See profiling section below.
- `ASSIMP_ENABLE=[<OFF>, ON]`: Enable integration with assimp, which is needed for some of the utilities in `extras`.
- `MANIFOLD_STRICT=[<OFF>, ON]`: Treat compile warnings as fatal build errors.

Dependency version override:
- `MANIFOLD_USE_BUILTIN_TBB=[<OFF>, ON]`: Use builtin version of tbb.
- `MANIFOLD_USE_BUILTIN_CLIPPER2=[<OFF>, ON]`: Use builtin version of clipper2.
- `MANIFOLD_USE_BUILTIN_NANOBIND=[<OFF>, ON]`: Use builtin version of nanobind.

> Note: These three options can force the build to avoid using the system
> version of the dependency. This will either use the provided source directory
> via `FETCHCONTENT_SOURCE_DIR_*` (see below), or fetch the source from GitHub.
> Note that the dependency will be built as static dependency to avoid dynamic
> library conflict. When the system package is unavailable, the option will be
> automatically set to true (except for tbb).

> WARNING: These packages are statically linked to the library, which may be
> unexpected for other consumers of the library. In particular, for tbb, this
> create two versions of tbb when another library also bring their own tbb,
> which may cause performance issues or crash the system.
> It is not recommended to install manifold compiled with builtin tbb, and this
> option requires explicit opt-in now.

Offline building (with missing dependencies/dependency version override):
- `MANIFOLD_DOWNLOADS=[OFF, <ON>]`: Automatically download missing dependencies.
  Need to set `FETCHCONTENT_SOURCE_DIR_*` if the dependency `*` is missing.
- `FETCHCONTENT_SOURCE_DIR_TBB`: path to tbb source (if `MANIFOLD_PAR` is enabled).
- `FETCHCONTENT_SOURCE_DIR_CLIPPER2`: path to tbb source (if `MANIFOLD_CROSS_SECTION` is enabled).
- `FETCHCONTENT_SOURCE_DIR_NANOBIND`: path to nanobind source (if `MANIFOLD_PYBIND` is enabled).
- `FETCHCONTENT_SOURCE_DIR_GOOGLETEST`: path to googletest source (if `MANIFOLD_TEST` is enabled).

> Note: When `FETCHCONTENT_SOURCE_DIR_*` is set, CMake will use the provided
> source directly without downloading regardless of the value of
> `MANIFOLD_DOWNLOADS`.

The build instructions used by our CI are in [manifold.yml](https://github.com/elalish/manifold/blob/master/.github/workflows/manifold.yml), which is a good source to check if something goes wrong and for instructions specific to other platforms, like Windows.

### WASM

> Note that we have only tested emscripten version 3.1.45. It is known that
  3.1.48 has some issues compiling manifold.

To build the JS WASM library, first install NodeJS and set up emscripten:

(on Mac):
```
brew install nodejs
brew install emscripten
```
(on Linux):
```
sudo apt install nodejs
git clone https://github.com/emscripten-core/emsdk.git
cd emsdk
./emsdk install latest
./emsdk activate latest
source ./emsdk/emsdk_env.sh
```
Then build:
```
cd manifold
mkdir buildWASM
cd buildWASM
emcmake cmake -DCMAKE_BUILD_TYPE=Release .. && emmake make
node test/manifold_test.js
emcmake cmake -DCMAKE_BUILD_TYPE=MinSizeRel .. && emmake make
cd test
node ./manifold_test.js
```

### Python

The CMake script will build the python binding `manifold3d` automatically. To
use the extension, please add `$BUILD_DIR/bindings/python` to your `PYTHONPATH`, where
`$BUILD_DIR` is the build directory for CMake. Examples using the python binding
can be found in `bindings/python/examples`. To see exported samples, run:
```
sudo apt install pkg-config libpython3-dev python3 python3-distutils python3-pip
pip install trimesh pytest
python3 run_all.py -e
```

Run the following code in the interpreter for
python binding documentation:

```
>>> import manifold3d
>>> help(manifold3d)
```

For more detailed documentation, please refer to the C++ API.

### Java / Clojure

Unofficial java bindings are currently maintained in [a fork](https://github.com/CommonWealthRobotics/manifold3d-java).

to build the java jars run:

```
bash scripts/makeJars.sh
```

To publish to Clojar

```
bash scripts/makeJars.sh && bash scripts/clojar.sh
```


### Windows Shenanigans

Windows users should build with `-DBUILD_SHARED_LIBS=OFF`, as enabling shared
libraries in general makes things very complicated.

The DLL file for manifoldc (C FFI bindings) when built with msvc is in `${CMAKE_BINARY_DIR}/bin/${BUILD_TYPE}/manifoldc.dll`.
For example, for the following command, the path relative to the project root directory is `build/bin/Release/manifoldc.dll`.
```sh
cmake . -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DMANIFOLD_DEBUG=ON -DMANIFOLD_PAR=${{matrix.parallel_backend}} -A x64 -B build
```

## Contributing

Contributions are welcome! A lower barrier contribution is to simply make a PR that adds a test, especially if it repros an issue you've found. Simply name it prepended with DISABLED_, so that it passes the CI. That will be a very strong signal to me to fix your issue. However, if you know how to fix it yourself, then including the fix in your PR would be much appreciated!

### Formatting

There is a formatting script `format.sh` that automatically formats everything.
It requires clang-format 11 and black formatter for python.

If you have clang-format installed but without clang-11, you can specify the
clang-format executable by setting the `CLANG_FORMAT` environment variable.

### Profiling

There is now basic support for the [Tracy profiler](https://github.com/wolfpld/tracy) for our tests.
To enable tracing, compile with `-DTRACY_ENABLE=on` cmake option, and run the test with Tracy server running.
To enable memory profiling in addition to tracing, compile with `-DTRACY_MEMORY_USAGE=ON` in addition to `-DTRACY_ENABLE=ON`.

### Fuzzing Support

We use https://github.com/google/fuzztest for fuzzing the triangulator.

To enable fuzzing, make sure that you are using clang compiler (`-DCMAKE_CXX_COMPILER=clang -DCMAKE_C_COMPILER=clang`), running Linux, and enable fuzzing support by setting `-DMANIFOLD_FUZZ=ON`.

To run the fuzzer and minimize testcase, do
```
../minimizer.sh ./test/polygon_fuzz --fuzz=PolygonFuzz.TriangulationNoCrash
```
### Fuzzing

To build with fuzzing support, you should set the following with CMake:

- Enable fuzzing by setting `-DMANIFOLD_FUZZ=ON`
- Disable python bindings by setting `-DMANIFOLD_PYBIND=OFF`
- Use `clang` for compiling by setting `-DCMAKE_CXX_COMPILER=clang++`
- You may need to disable parallelization by setting `-DMANIFOLD_PAR=OFF`, and set `ASAN_OPTIONS=detect_container_overflow=0` when building the binary on MacOS.

## About the author

This library was started by [Emmett Lalish](https://elalish.blogspot.com/), currently a senior rendering engineer at Wētā FX. This was my 20% project when I was a Google employee, though my day job was maintaining [\<model-viewer\>](https://modelviewer.dev/). I was the first employee at a 3D video startup, [Omnivor](https://www.omnivor.io/), and before that I worked on 3D printing at Microsoft, including [3D Builder](https://www.microsoft.com/en-us/p/3d-builder/9wzdncrfj3t6?activetab=pivot%3Aoverviewtab). Originally an aerospace engineer, I started at a small DARPA contractor doing seedling projects, one of which became [Sea Hunter](https://en.wikipedia.org/wiki/Sea_Hunter). I earned my doctorate from the University of Washington in control theory and published some [papers](https://www.researchgate.net/scientific-contributions/75011026_Emmett_Lalish).
