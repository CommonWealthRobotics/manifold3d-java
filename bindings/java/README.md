## Install

Maven: 

[![Clojars manifold3d](https://img.shields.io/clojars/v/com.github.madhephaestus/manifold3d.svg)](https://clojars.org/com.github.madhephaestus/manifold3d)
	

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

## Compilation


## Java / Clojure

Unofficial java bindings are currently maintained in [a fork](https://github.com/CommonWealthRobotics/manifold3d-java).

to build the java jars run:

```
bash ./scripts/buildC.sh && scripts/makeJars.sh
```

To publish to Clojar

```
bash ./scripts/buildC.sh && scripts/makeJars.sh && bash scripts/clojar.sh
```

