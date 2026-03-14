package com.example;

import java.lang.foreign.MemorySegment;

import com.cadoodlecad.manifold.ManifoldBindings;

import javafx.scene.shape.TriangleMesh;

public class ManifoldMesh {

	private final ManifoldBindings bindings;

	public ManifoldMesh(ManifoldBindings bindings) {
		this.bindings = bindings;
	}

	public TriangleMesh toJavaFXMesh(MemorySegment manifoldPtr) throws Throwable {
		ManifoldBindings.MeshData data = bindings.exportMeshGL(manifoldPtr);

		TriangleMesh mesh = new TriangleMesh();
		mesh.getPoints().setAll(data.vertices());
		mesh.getTexCoords().setAll(0, 0);

		int[] faces = new int[data.triCount() * 6];
		for (int i = 0; i < data.triCount(); i++) {
			int v0 = data.triangles()[i * 3 + 0];
			int v1 = data.triangles()[i * 3 + 1];
			int v2 = data.triangles()[i * 3 + 2];

			faces[i * 6 + 0] = v0;
			faces[i * 6 + 1] = 0;
			faces[i * 6 + 2] = v1;
			faces[i * 6 + 3] = 0;
			faces[i * 6 + 4] = v2;
			faces[i * 6 + 5] = 0;
		}
		mesh.getFaces().setAll(faces);

		return mesh;
	}
}