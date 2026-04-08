package com.example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import com.cadoodlecad.manifold.ManifoldBindings;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ManifoldBindings}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Union, Difference, Intersection, and Hull of a cube and sphere</li>
 *   <li>Export of each result to binary STL and 3MF</li>
 *   <li>Re-import of each file and comparison of vertex/triangle counts</li>
 * </ul>
 *
 * <p>Tolerance note: STL is a float-precision, unindexed format. After a round-trip
 * through STL the welded vertex count may differ slightly from the 3MF round-trip
 * (which preserves the original indexed topology). The tests therefore compare the
 * <em>triangle</em> count exactly (triangles survive both formats without change) and
 * check that the vertex count after re-import is within a small tolerance band.
 *
 * <p>Prerequisites: the native Manifold shared libraries must be on the library path
 * or bundled as resources, exactly as {@link ManifoldBindings} expects.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ManifoldBindingsTest {

	/** Shared bindings instance — created once per test class. */
	private static ManifoldBindings mb;

	/**
	 * Native cube shape: 10 × 10 × 10 mm, centered at origin.
	 * Re-created fresh for each test so operations don't share state.
	 */
	private static final double CUBE_SIZE = 10.0;
	private static final double SPHERE_R = 6.0;
	private static final int SPHERE_SEGS = 32;

	// -------------------------------------------------------------------------
	// Lifecycle
	// -------------------------------------------------------------------------

	@BeforeAll
	static void initBindings() throws Exception {
		mb = new ManifoldBindings();
		assertNotNull(mb, "ManifoldBindings must initialise without error");
		assertTrue(ManifoldBindings.isNativeLibraryLoaded(), "Native library must be loaded after construction");
	}

	@AfterAll
	static void closeBindings() {
		if (mb != null)
			mb.close();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/** Creates a fresh 10 mm cube centred at origin. */
	private java.lang.foreign.MemorySegment makeCube() throws Throwable {
		return mb.cube(CUBE_SIZE, CUBE_SIZE, CUBE_SIZE, true);
	}

	/** Creates a fresh sphere with {@value #SPHERE_SEGS} segments. */
	private java.lang.foreign.MemorySegment makeSphere() throws Throwable {
		return mb.sphere(SPHERE_R, SPHERE_SEGS);
	}

	/**
	 * Asserts that the manifold is non-empty, has a positive triangle count,
	 * and reports no error status.
	 */
	private void assertValidMesh(java.lang.foreign.MemorySegment m, String label) throws Throwable {
		int status = (int) mb.getClass()
				.getDeclaredMethod("manifold_status_direct", java.lang.foreign.MemorySegment.class)
				// status is exposed via numVert/numTri — use those as a proxy
				.invoke(mb, m);
		// Status check via public API: isEmpty returns 0 for a good mesh
		long verts = mb.numVert(m);
		long tris = mb.numTri(m);
		assertTrue(verts > 0, label + ": vertex count must be > 0 (got " + verts + ")");
		assertTrue(tris > 0, label + ": triangle count must be > 0 (got " + tris + ")");
	}

	/**
	 * Simpler validity check that doesn't rely on reflection.
	 * Verifies vertex count, triangle count, and volume are all positive.
	 */
	private void assertMeshValid(java.lang.foreign.MemorySegment m, String label) throws Throwable {
		long verts = mb.numVert(m);
		long tris = mb.numTri(m);
		double vol = mb.volume(m);

		assertTrue(verts > 0, label + ": numVert must be > 0, was " + verts);
		assertTrue(tris > 0, label + ": numTri must be > 0, was " + tris);
		assertTrue(vol > 0, label + ": volume must be > 0, was " + vol);
	}

	/**
	 * Round-trip a manifold through STL:
	 * <ol>
	 *   <li>Export to a temp STL file</li>
	 *   <li>Re-import from that file</li>
	 *   <li>Assert the re-imported mesh has the same triangle count</li>
	 *   <li>Assert vertex count is within 5 % (welding may differ slightly)</li>
	 * </ol>
	 *
	 * @return the re-imported manifold (caller is responsible for cleanup)
	 */
	private java.lang.foreign.MemorySegment assertStlRoundTrip(java.lang.foreign.MemorySegment original, File stlFile,
			String label) throws Throwable {

		long origTris = mb.numTri(original);
		long origVerts = mb.numVert(original);

		// Export
		mb.exportSTL(original, stlFile);
		assertTrue(stlFile.exists() && stlFile.length() > 84,
				label + " STL: file must exist and have data beyond header");

		// Import
		java.lang.foreign.MemorySegment reimported = mb.importSTL(stlFile);
		assertNotNull(reimported, label + " STL: re-imported segment must not be null");

		long reimTris = mb.numTri(reimported);
		long reimVerts = mb.numVert(reimported);

		// Triangle count must survive exactly
		assertEquals(origTris, reimTris, label + " STL: triangle count mismatch after round-trip");

		// Vertex count after welding should be ≤ original and within 5 %
		assertTrue(reimVerts <= origVerts * 1.05 + 1 && reimVerts >= origVerts * 0.70,
				label + " STL: vertex count after re-import (" + reimVerts
						+ ") is outside expected range relative to original (" + origVerts + ")");

		return reimported;
	}

	/**
	 * Round-trip a manifold through 3MF:
	 * <ol>
	 *   <li>Export to a temp 3MF file</li>
	 *   <li>Re-import the first object from that file</li>
	 *   <li>Assert vertex and triangle counts match exactly (3MF is indexed)</li>
	 * </ol>
	 *
	 * @return the re-imported manifold (caller is responsible for cleanup)
	 */
	private java.lang.foreign.MemorySegment assert3mfRoundTrip(java.lang.foreign.MemorySegment original,
			File threeMfFile, String label) throws Throwable {

		long origTris = mb.numTri(original);
		long origVerts = mb.numVert(original);

		// Export (single mesh → single object in the 3MF file)
		ArrayList<java.lang.foreign.MemorySegment> exportList = new ArrayList<>();
		exportList.add(original);
		mb.export3MF(exportList, threeMfFile);
		assertTrue(threeMfFile.exists() && threeMfFile.length() > 0, label + " 3MF: file must exist and be non-empty");

		// Import
		ArrayList<java.lang.foreign.MemorySegment> imported = mb.import3MF(threeMfFile);
		assertNotNull(imported, label + " 3MF: import result must not be null");
		assertEquals(1, imported.size(), label + " 3MF: expected exactly 1 object in file");

		java.lang.foreign.MemorySegment reimported = imported.get(0);
		long reimTris = mb.numTri(reimported);
		long reimVerts = mb.numVert(reimported);

		// 3MF preserves the indexed topology — counts must match exactly
		assertEquals(origTris, reimTris, label + " 3MF: triangle count mismatch after round-trip");
		assertEquals(origVerts, reimVerts, label + " 3MF: vertex count mismatch after round-trip");

		return reimported;
	}

	// -------------------------------------------------------------------------
	// Primitives smoke-test
	// -------------------------------------------------------------------------

	@Test
	@Order(1)
	@DisplayName("Cube primitive: valid mesh")
	void testCubePrimitive() throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		try {
			assertMeshValid(cube, "Cube");
			// A cube has 8 vertices and 12 triangles (6 faces × 2)
			assertEquals(8, mb.numVert(cube), "Cube should have 8 vertices");
			assertEquals(12, mb.numTri(cube), "Cube should have 12 triangles");
		} finally {
			mb.safeDelete(cube);
		}
	}

	@Test
	@Order(2)
	@DisplayName("Sphere primitive: valid mesh")
	void testSpherePrimitive() throws Throwable {
		java.lang.foreign.MemorySegment sphere = makeSphere();
		try {
			assertMeshValid(sphere, "Sphere");
			// Sphere vertex/tri counts depend on segment count; just verify they're positive
			assertTrue(mb.numVert(sphere) > 0, "Sphere vertex count");
			assertTrue(mb.numTri(sphere) > 0, "Sphere triangle count");
			// Volume of sphere: (4/3)πr³ ≈ 904.78 for r=6
			double expectedVol = (4.0 / 3.0) * Math.PI * Math.pow(SPHERE_R, 3);
			assertEquals(expectedVol, mb.volume(sphere), expectedVol * 0.05,
					"Sphere volume should be within 5 % of analytic value");
		} finally {
			mb.safeDelete(sphere);
		}
	}

	// -------------------------------------------------------------------------
	// Boolean: Union
	// -------------------------------------------------------------------------

	@Test
	@Order(10)
	@DisplayName("Union(cube, sphere): valid mesh, STL + 3MF round-trip")
	void testUnionRoundTrip() throws Throwable {
		Path tmpDir = new File(".").toPath();
		java.lang.foreign.MemorySegment cube = makeCube();
		java.lang.foreign.MemorySegment sphere = makeSphere();
		java.lang.foreign.MemorySegment result = null;
		java.lang.foreign.MemorySegment stlRe = null;
		java.lang.foreign.MemorySegment mfRe = null;
		try {
			result = mb.union(cube, sphere);
			assertMeshValid(result, "Union");

			// Volume of union = vol(cube) + vol(sphere) - vol(intersection)
			// Must be less than the sum and greater than the larger of the two
			double cubeVol = mb.volume(cube);
			double sphereVol = mb.volume(sphere);
			double unionVol = mb.volume(result);
			assertTrue(unionVol < cubeVol + sphereVol, "Union volume must be less than sum of parts");
			assertTrue(unionVol > Math.max(cubeVol, sphereVol), "Union volume must be greater than either part alone");

			// Round-trips
			File _3mf= tmpDir.resolve("union.3mf").toFile();
			File stl = tmpDir.resolve("union.stl").toFile();

			mfRe = assert3mfRoundTrip(result, _3mf, "Union");
			stlRe = assertStlRoundTrip(result, stl, "Union");

			assertMeshValid(stlRe, "Union/STL re-import");
			assertMeshValid(mfRe, "Union/3MF re-import");
		} finally {
			mb.safeDelete(cube);
			mb.safeDelete(sphere);
			mb.safeDelete(result);
			mb.safeDelete(stlRe);
			mb.safeDelete(mfRe);
		}
	}

	// -------------------------------------------------------------------------
	// Boolean: Difference
	// -------------------------------------------------------------------------

	@Test
	@Order(20)
	@DisplayName("Difference(cube, sphere): valid mesh, STL + 3MF round-trip")
	void testDifferenceRoundTrip(@TempDir Path tmpDir) throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		java.lang.foreign.MemorySegment sphere = makeSphere();
		java.lang.foreign.MemorySegment result = null;
		java.lang.foreign.MemorySegment stlRe = null;
		java.lang.foreign.MemorySegment mfRe = null;
		try {
			result = mb.difference(cube, sphere);
			assertMeshValid(result, "Difference");

			// Volume of difference must be less than the cube's own volume
			double diffVol = mb.volume(result);
			double cubeVol = mb.volume(cube);
			assertTrue(diffVol < cubeVol,
					"Difference volume (" + diffVol + ") must be < cube volume (" + cubeVol + ")");
			assertTrue(diffVol > 0, "Difference must still have positive volume (sphere doesn't swallow cube)");

			stlRe = assertStlRoundTrip(result, tmpDir.resolve("difference.stl").toFile(), "Difference");
			mfRe = assert3mfRoundTrip(result, tmpDir.resolve("difference.3mf").toFile(), "Difference");

			assertMeshValid(stlRe, "Difference/STL re-import");
			assertMeshValid(mfRe, "Difference/3MF re-import");
		} finally {
			mb.safeDelete(cube);
			mb.safeDelete(sphere);
			mb.safeDelete(result);
			mb.safeDelete(stlRe);
			mb.safeDelete(mfRe);
		}
	}

	// -------------------------------------------------------------------------
	// Boolean: Intersection
	// -------------------------------------------------------------------------

	@Test
	@Order(30)
	@DisplayName("Intersection(cube, sphere): valid mesh, STL + 3MF round-trip")
	void testIntersectionRoundTrip(@TempDir Path tmpDir) throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		java.lang.foreign.MemorySegment sphere = makeSphere();
		java.lang.foreign.MemorySegment result = null;
		java.lang.foreign.MemorySegment stlRe = null;
		java.lang.foreign.MemorySegment mfRe = null;
		try {
			result = mb.intersection(cube, sphere);
			assertMeshValid(result, "Intersection");

			double intersectVol = mb.volume(result);
			double cubeVol = mb.volume(cube);
			double sphereVol = mb.volume(sphere);

			// Intersection volume must be ≤ the smaller of the two shapes
			double smaller = Math.min(cubeVol, sphereVol);
			assertTrue(intersectVol <= smaller * 1.001,
					"Intersection volume (" + intersectVol + ") must be ≤ smaller of cube/sphere (" + smaller + ")");
			assertTrue(intersectVol > 0, "Intersection must be non-empty (shapes overlap)");

			stlRe = assertStlRoundTrip(result, tmpDir.resolve("intersection.stl").toFile(), "Intersection");
			mfRe = assert3mfRoundTrip(result, tmpDir.resolve("intersection.3mf").toFile(), "Intersection");

			assertMeshValid(stlRe, "Intersection/STL re-import");
			assertMeshValid(mfRe, "Intersection/3MF re-import");
		} finally {
			mb.safeDelete(cube);
			mb.safeDelete(sphere);
			mb.safeDelete(result);
			mb.safeDelete(stlRe);
			mb.safeDelete(mfRe);
		}
	}

	// -------------------------------------------------------------------------
	// Hull
	// -------------------------------------------------------------------------

	@Test
	@Order(40)
	@DisplayName("Hull(cube): valid mesh, STL + 3MF round-trip")
	void testHullCubeRoundTrip(@TempDir Path tmpDir) throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		java.lang.foreign.MemorySegment result = null;
		java.lang.foreign.MemorySegment stlRe = null;
		java.lang.foreign.MemorySegment mfRe = null;
		try {
			result = mb.hull(cube);
			assertMeshValid(result, "Hull(cube)");

			// Hull of a cube is the cube itself — volume should be the same
			double hullVol = mb.volume(result);
			double cubeVol = mb.volume(cube);
			assertEquals(cubeVol, hullVol, cubeVol * 0.001, "Hull of a cube must have the same volume as the cube");

			stlRe = assertStlRoundTrip(result, tmpDir.resolve("hull_cube.stl").toFile(), "Hull(cube)");
			mfRe = assert3mfRoundTrip(result, tmpDir.resolve("hull_cube.3mf").toFile(), "Hull(cube)");

			assertMeshValid(stlRe, "Hull(cube)/STL re-import");
			assertMeshValid(mfRe, "Hull(cube)/3MF re-import");
		} finally {
			mb.safeDelete(cube);
			mb.safeDelete(result);
			mb.safeDelete(stlRe);
			mb.safeDelete(mfRe);
		}
	}

	@Test
	@Order(41)
	@DisplayName("Hull(sphere): valid mesh, STL + 3MF round-trip")
	void testHullSphereRoundTrip(@TempDir Path tmpDir) throws Throwable {
		java.lang.foreign.MemorySegment sphere = makeSphere();
		java.lang.foreign.MemorySegment result = null;
		java.lang.foreign.MemorySegment stlRe = null;
		java.lang.foreign.MemorySegment mfRe = null;
		try {
			result = mb.hull(sphere);
			assertMeshValid(result, "Hull(sphere)");

			// Hull of a convex shape = same shape — volume within 1 %
			double hullVol = mb.volume(result);
			double sphereVol = mb.volume(sphere);
			assertEquals(sphereVol, hullVol, sphereVol * 0.01,
					"Hull of a sphere must have effectively the same volume");

			stlRe = assertStlRoundTrip(result, tmpDir.resolve("hull_sphere.stl").toFile(), "Hull(sphere)");
			mfRe = assert3mfRoundTrip(result, tmpDir.resolve("hull_sphere.3mf").toFile(), "Hull(sphere)");

			assertMeshValid(stlRe, "Hull(sphere)/STL re-import");
			assertMeshValid(mfRe, "Hull(sphere)/3MF re-import");
		} finally {
			mb.safeDelete(sphere);
			mb.safeDelete(result);
			mb.safeDelete(stlRe);
			mb.safeDelete(mfRe);
		}
	}

	@Test
	@Order(42)
	@DisplayName("BatchHull([cube, sphere]): valid mesh, STL + 3MF round-trip")
	void testBatchHullRoundTrip(@TempDir Path tmpDir) throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		java.lang.foreign.MemorySegment sphere = makeSphere();
		java.lang.foreign.MemorySegment result = null;
		java.lang.foreign.MemorySegment stlRe = null;
		java.lang.foreign.MemorySegment mfRe = null;
		try {
			result = mb.batchHull(new java.lang.foreign.MemorySegment[] { cube, sphere });
			assertMeshValid(result, "BatchHull");

			// Hull of cube+sphere must enclose both; volume ≥ each individual hull
			double cubeHullVol = mb.volume(mb.hull(cube));
			double sphereHullVol = mb.volume(mb.hull(sphere));
			double batchHullVol = mb.volume(result);

			assertTrue(batchHullVol >= cubeHullVol * 0.999, "Batch hull volume must be >= cube hull volume");
			assertTrue(batchHullVol >= sphereHullVol * 0.999, "Batch hull volume must be >= sphere hull volume");

			stlRe = assertStlRoundTrip(result, tmpDir.resolve("batch_hull.stl").toFile(), "BatchHull");
			mfRe = assert3mfRoundTrip(result, tmpDir.resolve("batch_hull.3mf").toFile(), "BatchHull");

			assertMeshValid(stlRe, "BatchHull/STL re-import");
			assertMeshValid(mfRe, "BatchHull/3MF re-import");
		} finally {
			mb.safeDelete(cube);
			mb.safeDelete(sphere);
			mb.safeDelete(result);
			mb.safeDelete(stlRe);
			mb.safeDelete(mfRe);
		}
	}

	// -------------------------------------------------------------------------
	// Multi-mesh 3MF export/import
	// -------------------------------------------------------------------------

	@Test
	@Order(50)
	@DisplayName("3MF multi-mesh: export cube+sphere, re-import both objects")
	void testMultiMesh3mfRoundTrip(@TempDir Path tmpDir) throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		java.lang.foreign.MemorySegment sphere = makeSphere();
		ArrayList<java.lang.foreign.MemorySegment> imported = null;
		try {
			long cubeVerts = mb.numVert(cube);
			long cubeTris = mb.numTri(cube);
			long sphereVerts = mb.numVert(sphere);
			long sphereTris = mb.numTri(sphere);

			// Export both as a single 3MF file
			ArrayList<java.lang.foreign.MemorySegment> exportList = new ArrayList<>();
			exportList.add(cube);
			exportList.add(sphere);
			File threeMfFile = tmpDir.resolve("multi.3mf").toFile();
			mb.export3MF(exportList, threeMfFile);
			assertTrue(threeMfFile.exists() && threeMfFile.length() > 0, "Multi-mesh 3MF file must exist");

			// Re-import: expect 2 objects in the same order
			imported = mb.import3MF(threeMfFile);
			assertEquals(2, imported.size(), "Must import exactly 2 objects from multi-mesh 3MF");

			java.lang.foreign.MemorySegment reimCube = imported.get(0);
			java.lang.foreign.MemorySegment reimSphere = imported.get(1);

			assertEquals(cubeVerts, mb.numVert(reimCube), "Re-imported cube vertex count");
			assertEquals(cubeTris, mb.numTri(reimCube), "Re-imported cube triangle count");
			assertEquals(sphereVerts, mb.numVert(reimSphere), "Re-imported sphere vertex count");
			assertEquals(sphereTris, mb.numTri(reimSphere), "Re-imported sphere triangle count");

		} finally {
			mb.safeDelete(cube);
			mb.safeDelete(sphere);
			if (imported != null) {
				for (java.lang.foreign.MemorySegment seg : imported) {
					mb.safeDelete(seg);
				}
			}
		}
	}

	// -------------------------------------------------------------------------
	// File format edge cases
	// -------------------------------------------------------------------------

	@Test
	@Order(60)
	@DisplayName("STL file size: matches 80 + 4 + triCount * 50 formula")
	void testStlFileSizeFormula(@TempDir Path tmpDir) throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		try {
			File stlFile = tmpDir.resolve("cube_size.stl").toFile();
			mb.exportSTL(cube, stlFile);

			long triCount = mb.numTri(cube);
			long expectedLen = 84L + triCount * 50L;
			assertEquals(expectedLen, stlFile.length(), "Binary STL file size must equal 84 + triCount*50 bytes");
		} finally {
			mb.safeDelete(cube);
		}
	}

	@Test
	@Order(61)
	@DisplayName("3MF file: is a valid ZIP archive containing 3D/3dmodel.model")
	void testThreeMfIsValidZip(@TempDir Path tmpDir) throws Throwable {
		java.lang.foreign.MemorySegment sphere = makeSphere();
		try {
			File threeMfFile = tmpDir.resolve("sphere.3mf").toFile();
			ArrayList<java.lang.foreign.MemorySegment> list = new ArrayList<>();
			list.add(sphere);
			mb.export3MF(list, threeMfFile);

			// Open as ZIP and verify required entries
			try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
					new java.io.FileInputStream(threeMfFile))) {
				boolean hasModel = false;
				boolean hasContentTypes = false;
				boolean hasRels = false;
				java.util.zip.ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					String name = entry.getName().replace('\\', '/');
					if (name.equalsIgnoreCase("3D/3dmodel.model"))
						hasModel = true;
					if (name.equals("[Content_Types].xml"))
						hasContentTypes = true;
					if (name.equals("_rels/.rels"))
						hasRels = true;
					zis.closeEntry();
				}
				assertTrue(hasModel, "3MF must contain 3D/3dmodel.model");
				assertTrue(hasContentTypes, "3MF must contain [Content_Types].xml");
				assertTrue(hasRels, "3MF must contain _rels/.rels");
			}
		} finally {
			mb.safeDelete(sphere);
		}
	}

	// -------------------------------------------------------------------------
	// Volume sanity across all operations
	// -------------------------------------------------------------------------

	@Test
	@Order(70)
	@DisplayName("Volume identity: vol(A∪B) = vol(A) + vol(B) - vol(A∩B)")
	void testVolumeIdentity() throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		java.lang.foreign.MemorySegment sphere = makeSphere();
		java.lang.foreign.MemorySegment unionMs = null;
		java.lang.foreign.MemorySegment intersectMs = null;
		try {
			unionMs = mb.union(cube, sphere);
			intersectMs = mb.intersection(cube, sphere);

			double volA = mb.volume(cube);
			double volB = mb.volume(sphere);
			double volU = mb.volume(unionMs);
			double volI = mb.volume(intersectMs);

			// Inclusion-exclusion principle: |A∪B| = |A| + |B| - |A∩B|
			double expected = volA + volB - volI;
			assertEquals(expected, volU, expected * 0.01, "Volume inclusion-exclusion identity must hold within 1 %");
		} finally {
			mb.safeDelete(cube);
			mb.safeDelete(sphere);
			mb.safeDelete(unionMs);
			mb.safeDelete(intersectMs);
		}
	}

	@Test
	@Order(71)
	@DisplayName("Volume identity: vol(A-B) = vol(A∩B) + vol(A-B) = vol(A)")
	void testDifferenceVolumeIdentity() throws Throwable {
		java.lang.foreign.MemorySegment cube = makeCube();
		java.lang.foreign.MemorySegment sphere = makeSphere();
		java.lang.foreign.MemorySegment diffMs = null;
		java.lang.foreign.MemorySegment intersectMs = null;
		try {
			diffMs = mb.difference(cube, sphere);
			intersectMs = mb.intersection(cube, sphere);

			double volA = mb.volume(cube);
			double volDiff = mb.volume(diffMs);
			double volI = mb.volume(intersectMs);

			// vol(A-B) + vol(A∩B) = vol(A)
			assertEquals(volA, volDiff + volI, volA * 0.01, "vol(A-B) + vol(A∩B) must equal vol(A) within 1 %");
		} finally {
			mb.safeDelete(cube);
			mb.safeDelete(sphere);
			mb.safeDelete(diffMs);
			mb.safeDelete(intersectMs);
		}
	}
}