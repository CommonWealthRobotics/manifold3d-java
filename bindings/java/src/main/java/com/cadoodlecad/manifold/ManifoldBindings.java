package com.cadoodlecad.manifold;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ManifoldBindings {

	private static boolean loaded = false;

	private static final Linker LINKER = Linker.nativeLinker();

	private final SymbolLookup library;
	private final Map<String, MethodHandle> functions = new HashMap<>();

	private static final StructLayout VEC3_LAYOUT = MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE,
			ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

	private static final StructLayout PAIR_LAYOUT = MemoryLayout.structLayout(ValueLayout.ADDRESS.withName("first"),
			ValueLayout.ADDRESS.withName("second"));

	public static final int OPTYPE_UNION = 0;
	public static final int OPTYPE_DIFFERENCE = 1;
	public static final int OPTYPE_INTERSECTION = 2;

	private static void loadNativeLibrary(String libName, File cacheDirectory) throws Exception {
		try {
			// Detect platform
			String os = System.getProperty("os.name").toLowerCase();
			String arch = System.getProperty("os.arch").toLowerCase();

			if (arch.equals("amd64"))
				arch = "x86_64";
			if (arch.contains("aarch"))
				arch = "arm64";
			String platform;
			String extension;
			if (os.contains("win")) {
				platform = "win-" + arch;
				extension = ".dll";
				if (libName.startsWith("lib")) {
					libName = libName.substring(3, libName.length());
				}
			} else if (os.contains("mac")) {
				platform = "mac-" + arch;
				extension = ".dylib";
			} else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
				platform = "linux-" + arch;
				extension = ".so";
			} else {
				throw new RuntimeException("Unsupported OS: " + os);
			}

			// Add library extension
			String fullName = libName + extension;

			// Copy libraries to lib directory
			java.io.File libsDir = cacheDirectory == null ? Files.createTempDirectory("manifold3d").toFile()
					: cacheDirectory;
			if (cacheDirectory == null)
				libsDir.deleteOnExit();
			if (!libsDir.exists())
				libsDir.mkdirs();

			java.io.File libFile = new java.io.File(libsDir, fullName);
			java.io.File devFile = new java.io.File(
					"src/main/resources/manifold3d/natives/" + platform + "/" + fullName);

			System.out.println("Searching library: " + devFile.getAbsolutePath());

			// Check timestamp, copy only newer
			boolean needsCopy = !libFile.exists();
			if (!needsCopy && devFile.exists())
				needsCopy = devFile.lastModified() > libFile.lastModified();

			if (needsCopy) {
				//				if (devFile.exists()) {
				//					java.nio.file.Files.copy(devFile.toPath(), libFile.toPath(), 
				//						java.nio.file.StandardCopyOption.REPLACE_EXISTING,
				//						java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
				//						libFile.setExecutable(true);
				//				} else {
				try (java.io.InputStream in = ManifoldBindings.class
						.getResourceAsStream("/manifold3d/natives/" + platform + "/" + fullName)) {
					if (in == null)
						throw new RuntimeException("Library not found: " + fullName + " for platform " + platform);

					java.nio.file.Files.copy(in, libFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					System.out.println("Extracted to libs/: " + fullName);
					if (cacheDirectory == null)
						libFile.deleteOnExit();
				}
				// }
			} else {
				System.out.println("Copy not performed, already in cache");
			}
			System.out.println("Loading library " + libFile.getAbsolutePath());
			System.load(libFile.getAbsolutePath());
			loaded = true;
		} catch (Exception e) {
			throw new RuntimeException("Failed to load: " + libName, e);
		}
	}

	public static void loadNativeLibrarys(File cacheDirectory) throws Exception {
		loadNativeLibrary("libmanifold", cacheDirectory);
		loadNativeLibrary("libmanifoldc", cacheDirectory);
	}

	public ManifoldBindings() throws Exception {
		this((File) null);
	}

	public ManifoldBindings(File cacheDirectory) throws Exception {
		if (!isNativeLibraryLoaded()) {
			loadNativeLibrary("libmanifold", cacheDirectory);
			loadNativeLibrary("libmanifoldc", cacheDirectory);
		}

		this.library = SymbolLookup.loaderLookup();

		// ===== Allocation/construction =====

		// ManifoldManifold* manifold_of_meshgl(void* mem, ManifoldMeshGL* mesh);
		load("manifold_of_meshgl", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_of_meshgl64(void* mem, ManifoldMeshGL64* mesh);
		load("manifold_of_meshgl64", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_alloc_manifold();
		load("manifold_alloc_manifold", ValueLayout.ADDRESS);

		// ManifoldMeshGL* manifold_alloc_meshgl();
		load("manifold_alloc_meshgl", ValueLayout.ADDRESS);

		// ManifoldMeshGL64* manifold_alloc_meshgl64();
		load("manifold_alloc_meshgl64", ValueLayout.ADDRESS);

		// ManifoldManifoldVec* manifold_alloc_manifold_vec();
		load("manifold_alloc_manifold_vec", ValueLayout.ADDRESS);

		// ManifoldBox* manifold_alloc_box();
		load("manifold_alloc_box", ValueLayout.ADDRESS);

		// ===== Primitives =====

		// ManifoldManifold* manifold_copy(void* mem, ManifoldManifold* m);
		load("manifold_copy", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_empty(void* mem);
		load("manifold_empty", ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_tetrahedron(void* mem);
		load("manifold_tetrahedron", ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_cube(void* mem, double x, double y, double z, int
		// center);
		load("manifold_cube", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT);

		// ManifoldManifold* manifold_sphere(void* mem, double radius, int
		// circular_segments);
		load("manifold_sphere", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE,
				ValueLayout.JAVA_INT);

		// ManifoldManifold* manifold_cylinder(void* mem, double height, double
		// radius_low, double radius_high, int circular_segments, int center);
		load("manifold_cylinder", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);

		// ===== Boolean operations =====

		// ManifoldManifold* manifold_boolean(void* mem, ManifoldManifold* a,
		// ManifoldManifold* b, ManifoldOpType op); OpType : char { Add, Subtract,
		// Intersect };
		load("manifold_boolean", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT);

		// ManifoldManifold* manifold_union(void* mem, ManifoldManifold* a,
		// ManifoldManifold* b);
		load("manifold_union", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_difference(void* mem, ManifoldManifold* a,
		// ManifoldManifold* b);
		load("manifold_difference", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_intersection(void* mem, ManifoldManifold* a,
		// ManifoldManifold* b);
		load("manifold_intersection", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_batch_boolean(void* mem, ManifoldManifoldVec* ms,
		// ManifoldOpType op);
		load("manifold_batch_boolean", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT);

		// ===== Vector operations =====

		// void manifold_manifold_vec_push_back(ManifoldManifoldVec* ms,
		// ManifoldManifold* m);
		loadVoid("manifold_manifold_vec_push_back", ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ===== Transforms =====

		// ManifoldManifold* manifold_transform(void* mem, ManifoldManifold* m, double
		// x1, double y1, double z1, double x2, double y2, double z2, double x3, double
		// y3, double z3, double x4, double y4, double z4);
		load("manifold_transform", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, // men, m,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, // row 1 (x1,y1,z1)
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, // row 2 (x2,y2,z2)
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, // row 3 (x3,y3,z3)
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE); // row 4 (x4,y4,z4) -
																							// translation

		// ManifoldManifold* manifold_translate(void* mem, ManifoldManifold* m, double
		// x, double y, double z);
		load("manifold_translate", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

		// ManifoldManifold* manifold_scale(void* mem, ManifoldManifold* m, double x,
		// double y, double z);
		load("manifold_scale", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

		// ManifoldManifold* manifold_mirror(void* mem, ManifoldManifold* m, double nx,
		// double ny, double nz);
		load("manifold_mirror", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

		// ManifoldManifold* manifold_rotate(void* mem, ManifoldManifold* m, double x,
		// double y, double z);
		load("manifold_rotate", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

		// ===== Refinement =====

		// ManifoldManifold* manifold_refine(void* mem, ManifoldManifold* m, int
		// refine); refine > 1
		load("manifold_refine", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

		// ManifoldManifold* manifold_refine_to_length(void* mem, ManifoldManifold* m,
		// double length);
		load("manifold_refine_to_length", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_DOUBLE);

		// ManifoldManifold* manifold_refine_to_tolerance(void* mem, ManifoldManifold*
		// m, double tolerance);
		load("manifold_refine_to_tolerance", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_DOUBLE);

		// ManifoldManifold* manifold_smooth_by_normals(void* mem, ManifoldManifold* m,
		load("manifold_smooth_by_normals", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT);

		// ManifoldManifold* manifold_calculate_normals(void* mem, ManifoldManifold* m,
		// int normal_idx, double min_sharp_angle);
		load("manifold_calculate_normals", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE);

		// ===== Split/Trim operations =====

		// ManifoldManifoldPair manifold_split(void* mem_first, void* mem_second,
		// ManifoldManifold* a, ManifoldManifold* b);
		load("manifold_split", PAIR_LAYOUT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_trim_by_plane(void* mem, ManifoldManifold* m,
		// double normal_x, double normal_y, double normal_z, double offset);
		load("manifold_trim_by_plane", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

		// ManifoldManifoldPair manifold_split_by_plane(void* mem_first, void*
		// mem_second, ManifoldManifold* m, double normal_x, double normal_y, double
		// normal_z, double offset);
		load("manifold_split_by_plane", PAIR_LAYOUT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
				ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

		// ManifoldPolygons* manifold_slice(void* mem, ManifoldManifold* m, double
		// height);
		load("manifold_slice", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE);

		// ===== Analysis =====

		// double manifold_volume(ManifoldManifold* m);
		load("manifold_volume", ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS);

		// double manifold_surface_area(ManifoldManifold* m);
		load("manifold_surface_area", ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS);

		// ManifoldBox* manifold_bounding_box(void* mem, ManifoldManifold* m);
		load("manifold_bounding_box", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldVec3 manifold_box_min(ManifoldBox* b);
		load("manifold_box_min", VEC3_LAYOUT, ValueLayout.ADDRESS);

		// ManifoldVec3 manifold_box_max(ManifoldBox* b);
		load("manifold_box_max", VEC3_LAYOUT, ValueLayout.ADDRESS);

		// ManifoldVec3 manifold_box_center(ManifoldBox* b);
		load("manifold_box_center", VEC3_LAYOUT, ValueLayout.ADDRESS);

		// ManifoldVec3 manifold_box_dimensions(ManifoldBox* b);
		load("manifold_box_dimensions", VEC3_LAYOUT, ValueLayout.ADDRESS);

		// double manifold_epsilon(ManifoldManifold* m);
		load("manifold_epsilon", ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS);

		// int manifold_genus(ManifoldManifold* m);
		load("manifold_genus", ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

		// ===== Composition/Decomposition =====

		// ManifoldManifold* manifold_compose(void* mem, ManifoldManifoldVec* ms);
		load("manifold_compose", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifoldVec* manifold_decompose(void* mem, ManifoldManifold* m);
		load("manifold_decompose", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_as_original(void* mem, ManifoldManifold* m);
		load("manifold_as_original", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ===== Hull =====

		// ManifoldManifold* manifold_hull(void* mem, ManifoldManifold* m);
		load("manifold_hull", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_batch_hull(void* mem, ManifoldManifoldVec* ms);
		load("manifold_batch_hull", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ===== Info =====

		// size_t manifold_manifold_size(); Should return 16 bytes
		load("manifold_manifold_size", ValueLayout.JAVA_LONG);

		// size_t manifold_meshgl_size(); Should return 232 bytes
		load("manifold_meshgl_size", ValueLayout.JAVA_LONG);

		// size_t manifold_meshgl64_size(); Should return 232 bytes
		load("manifold_meshgl64_size", ValueLayout.JAVA_LONG);

		// size_t manifold_num_edge(ManifoldManifold* m);
		load("manifold_num_edge", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_num_prop(ManifoldManifold* m);
		load("manifold_num_prop", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_num_vert(ManifoldManifold* m);
		load("manifold_num_vert", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_num_tri(ManifoldManifold* m);
		load("manifold_num_tri", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// ManifoldError manifold_status(ManifoldManifold* m);
		load("manifold_status", ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

		// int manifold_is_empty(ManifoldManifold* m);
		load("manifold_is_empty", ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

		// size_t manifold_manifold_vec_size(); Should return 8
		load("manifold_manifold_vec_size", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// ManifoldManifold* manifold_manifold_vec_get(void* mem, ManifoldManifoldVec*
		// ms, size_t idx);
		load("manifold_manifold_vec_get", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

		// ===== Mesh export =====

		// ManifoldMeshGL* manifold_get_meshgl(void* mem, ManifoldManifold* m);
		load("manifold_get_meshgl", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldMeshGL* manifold_get_meshgl64(void* mem, ManifoldManifold* m);
		load("manifold_get_meshgl64", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// size_t manifold_meshgl_num_vert(ManifoldMeshGL* m);
		load("manifold_meshgl_num_vert", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl64_num_vert(ManifoldMeshGL* m);
		load("manifold_meshgl64_num_vert", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl_num_tri(ManifoldMeshGL* m);
		load("manifold_meshgl_num_tri", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl64_num_tri(ManifoldMeshGL* m);
		load("manifold_meshgl64_num_tri", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl_num_prop(ManifoldMeshGL* m);
		load("manifold_meshgl_num_prop", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl64_num_prop(ManifoldMeshGL* m);
		load("manifold_meshgl64_num_prop", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl_vert_properties_length(ManifoldMeshGL* m);
		load("manifold_meshgl_vert_properties_length", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl64_vert_properties_length(ManifoldMeshGL* m);
		load("manifold_meshgl64_vert_properties_length", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl_tri_length(ManifoldMeshGL* m);
		load("manifold_meshgl_tri_length", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_meshgl64_tri_length(ManifoldMeshGL* m);
		load("manifold_meshgl64_tri_length", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// float* manifold_meshgl_vert_properties(void* mem, ManifoldMeshGL* m);
		load("manifold_meshgl_vert_properties", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// float* manifold_meshgl64_vert_properties(void* mem, ManifoldMeshGL* m);
		load("manifold_meshgl64_vert_properties", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// uint32_t* manifold_meshgl_tri_verts(void* mem, ManifoldMeshGL* m);
		load("manifold_meshgl_tri_verts", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// uint32_t* manifold_meshgl64_tri_verts(void* mem, ManifoldMeshGL* m);
		load("manifold_meshgl64_tri_verts", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldMeshGL* manifold_meshgl_merge(void* mem, ManifoldMeshGL* m);
		load("manifold_meshgl_merge", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ManifoldMeshGL64* manifold_meshgl64_merge(void* mem, ManifoldMeshGL64* m);
		load("manifold_meshgl64_merge", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		// ===== Cleanup ======

		// void manifold_delete_manifold(ManifoldManifold* m);
		loadVoid("manifold_delete_manifold", ValueLayout.ADDRESS);

		// void manifold_delete_meshgl(ManifoldMeshGL* m);
		loadVoid("manifold_delete_meshgl", ValueLayout.ADDRESS);

		// void manifold_delete_meshgl64(ManifoldMeshGL64* m);
		loadVoid("manifold_delete_meshgl64", ValueLayout.ADDRESS);

		// void manifold_delete_manifold_vec(ManifoldManifoldVec* ms);
		loadVoid("manifold_delete_manifold_vec", ValueLayout.ADDRESS);

		// void manifold_delete_box(ManifoldBox* b);
		loadVoid("manifold_delete_box", ValueLayout.ADDRESS);

		// void manifold_delete_polygons(ManifoldPolygons* p);
		loadVoid("manifold_delete_polygons", ValueLayout.ADDRESS);

		// ManifoldMeshGL* manifold_meshgl(void* mem, float* vert_props, size_t n_verts,
		// size_t n_props, uint32_t* tri_verts, size_t n_tris);
		load("manifold_meshgl", ValueLayout.ADDRESS, // return: ManifoldMeshGL*
				ValueLayout.ADDRESS, // arg1: void* mem
				ValueLayout.ADDRESS, // arg2: float* vert_props
				ValueLayout.JAVA_LONG, // arg3: size_t n_verts
				ValueLayout.JAVA_LONG, // arg4: size_t n_props
				ValueLayout.ADDRESS, // arg5: uint32_t* tri_verts
				ValueLayout.JAVA_LONG // arg6: size_t n_tris
		);

		// ManifoldMeshGL64* manifold_meshgl64(void* mem, double* vert_props, size_t
		// n_verts, size_t n_props, uint64_t* tri_verts, size_t n_tris);
		load("manifold_meshgl64", ValueLayout.ADDRESS, // return: ManifoldMeshGL*
				ValueLayout.ADDRESS, // arg1: void* mem
				ValueLayout.ADDRESS, // arg2: double* vert_props
				ValueLayout.JAVA_LONG, // arg3: size_t n_verts
				ValueLayout.JAVA_LONG, // arg4: size_t n_props
				ValueLayout.ADDRESS, // arg5: uint64_t* tri_verts
				ValueLayout.JAVA_LONG // arg6: size_t n_tris
		);

		System.out.println("Available Manifold functions: " + functions.keySet());

	}

	public void deleteMeshGL64(MemorySegment seg) {
		if (seg == null)
			return;
		try {
			functions.get("manifold_delete_meshgl64").invoke(seg);
		} catch (Throwable ignored) {
		}
	}

	// Data structure
	public record MeshData64(double[] vertices, long[] triangles, int vertCount, int triCount) {
	}

	public MemorySegment importMeshGL64(double[] vertices, long[] triangles, long nVerts, long nTris) throws Throwable {
		MethodHandle mh = functions.get("manifold_meshgl64");
		if (mh == null)
			throw new RuntimeException("manifold_meshgl64 not found");

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment vertPtr = arena.allocate(3 * nVerts * Double.BYTES);
			vertPtr.copyFrom(MemorySegment.ofArray(vertices));

			MemorySegment triPtr = arena.allocate(3 * nTris * Long.BYTES);
			triPtr.copyFrom(MemorySegment.ofArray(triangles));

			MemorySegment meshGLmem = (MemorySegment) functions.get("manifold_alloc_meshgl64").invoke();
			MemorySegment meshGL = null;

			try {
				meshGL = (MemorySegment) mh.invoke(meshGLmem, vertPtr, nVerts, 3L, triPtr, nTris);

				MemorySegment mergedMem = (MemorySegment) functions.get("manifold_alloc_meshgl64").invoke();
				MemorySegment merged = null;

				try {
					merged = (MemorySegment) functions.get("manifold_meshgl64_merge").invoke(mergedMem, meshGL);

					MemorySegment manMem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
					MemorySegment result = (MemorySegment) functions.get("manifold_of_meshgl64").invoke(manMem, merged);

					try {
						functions.get("manifold_delete_meshgl64").invoke(merged);
					} catch (Throwable ignored) {
					}
					try {
						functions.get("manifold_delete_meshgl64").invoke(meshGL);
					} catch (Throwable ignored) {
					}

					return result;

				} catch (Throwable e) {
					if (merged != null)
						try {
							functions.get("manifold_delete_meshgl64").invoke(merged);
						} catch (Throwable ignored) {
						}
					throw e;
				}
			} catch (Throwable e) {
				if (meshGL != null)
					try {
						functions.get("manifold_delete_meshgl64").invoke(meshGL);
					} catch (Throwable ignored) {
					}
				throw e;
			}
		}
	}

	public MeshData64 exportMeshGL64(MemorySegment manifold) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_meshgl64").invoke();
		MemorySegment meshGL = (MemorySegment) functions.get("manifold_get_meshgl64").invoke(mem, manifold);

		try {
			long numVert = (long) functions.get("manifold_meshgl64_num_vert").invoke(meshGL);
			long numTri = (long) functions.get("manifold_meshgl64_num_tri").invoke(meshGL);
			long numProp = (long) functions.get("manifold_meshgl64_num_prop").invoke(meshGL);

			double[] vertices = new double[(int) (3 * numVert)];
			long[] triangles = new long[(int) (3 * numTri)];

			try (Arena temp = Arena.ofConfined()) {
				if (numVert > 0) {
					long vertLen = (long) functions.get("manifold_meshgl64_vert_properties_length").invoke(meshGL);
					MemorySegment tempMem = temp.allocate(vertLen * Double.BYTES);
					functions.get("manifold_meshgl64_vert_properties").invoke(tempMem, meshGL);

					for (int i = 0; i < numVert; i++)
						for (int j = 0; (j < 3) && (j < numProp); j++)
							vertices[i * 3 + j] = tempMem.getAtIndex(ValueLayout.JAVA_DOUBLE, i * numProp + j);
				}

				if (numTri > 0) {
					long triLen = (long) functions.get("manifold_meshgl64_tri_length").invoke(meshGL);
					MemorySegment tempMem = temp.allocate(triLen * Long.BYTES);
					functions.get("manifold_meshgl64_tri_verts").invoke(tempMem, meshGL);

					for (int i = 0; i < triLen; i++)
						triangles[i] = tempMem.getAtIndex(ValueLayout.JAVA_LONG, i);
				}
			}

			return new MeshData64(vertices, triangles, (int) numVert, (int) numTri);

		} finally {
			try {
				functions.get("manifold_delete_meshgl64").invoke(meshGL);
			} catch (Throwable ignored) {
			}
		}
	}

	// load
	private void load(String name, MemoryLayout returnLayout, MemoryLayout... argLayouts) {
		try {
			MemorySegment symbol = library.find(name).orElseThrow();
			FunctionDescriptor desc = FunctionDescriptor.of(returnLayout, argLayouts);
			functions.put(name, LINKER.downcallHandle(symbol, desc));
		} catch (Exception e) {
			System.err.println("ERROR: Missing Manifold function " + name);
		}
	}

	// loadVoid - Some load calls have void return type
	private void loadVoid(String name, ValueLayout... argLayouts) {
		try {
			MemorySegment symbol = library.find(name).orElseThrow();
			FunctionDescriptor desc = FunctionDescriptor.ofVoid(argLayouts);
			functions.put(name, LINKER.downcallHandle(symbol, desc));
		} catch (Exception e) {
			System.err.println("ERROR: Missing Manifold function " + name);
		}
	}

	// ===== Primitives =====

	// ManifoldManifold* manifold_empty(void* mem);
	public MemorySegment empty() throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_empty").invoke(mem);
	}

	// ManifoldManifold* manifold_tetrahedron(void* mem);
	public MemorySegment tetrahedron() throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_tetrahedron").invoke(mem);
	}

	// ManifoldManifold* manifold_cube(void* mem, double x, double y, double z, int
	// center);
	public MemorySegment cube(double x, double y, double z, boolean center) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_cube").invoke(mem, x, y, z, center ? 1 : 0);
	}

	// ManifoldManifold* manifold_sphere(void* mem, double radius, int
	// circular_segments);
	public MemorySegment sphere(double radius, int segments) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_sphere").invoke(mem, radius, segments);
	}

	// ManifoldManifold* manifold_cylinder(void* mem, double height, double
	// radius_low, double radius_high, int circular_segments, int center);
	public MemorySegment cylinder(double height, double radiusLow, double radiusHigh, int segments, int center)
			throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_cylinder").invoke(mem, height, radiusLow, radiusHigh, segments,
				center);
	}

	// ===== Transformations =====
	// 4x4 matrix transform (12 doubles = 3x4 affine matrix, last row implied
	// 0,0,0,1)
	// Matrix layout: [x1 y1 z1]
	// [x2 y2 z2]
	// [x3 y3 z3]
	// [x4 y4 z4] <- translation
	// ManifoldManifold* manifold_transform(void* mem, ManifoldManifold* m, double
	// x1, double y1, double z1, double x2, double y2, double z2, double x3, double
	// y3, double z3, double x4, double y4, double z4);
	public MemorySegment transform(MemorySegment m, double x1, double y1, double z1, double x2, double y2, double z2,
			double x3, double y3, double z3, double x4, double y4, double z4) throws Throwable {

		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_transform").invoke(mem, m, x1, y1, z1, x2, y2, z2, x3, y3, z3,
				x4, y4, z4);
	}

	// ManifoldManifold* manifold_translate(void* mem, ManifoldManifold* m, double
	// x, double y, double z);
	public MemorySegment translate(MemorySegment m, double x, double y, double z) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_translate").invoke(mem, m, x, y, z);
	}

	// ManifoldManifold* manifold_scale(void* mem, ManifoldManifold* m, double x,
	// double y, double z);
	public MemorySegment scale(MemorySegment m, double x, double y, double z) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_scale").invoke(mem, m, x, y, z);
	}

	// ManifoldManifold* manifold_rotate(void* mem, ManifoldManifold* m, double x,
	// double y, double z);
	public MemorySegment rotate(MemorySegment m, double x, double y, double z) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_rotate").invoke(mem, m, x, y, z);
	}

	public MemorySegment rotateX(MemorySegment m, double degrees) throws Throwable {
		return rotate(m, degrees, 0, 0);
	}

	public MemorySegment rotateY(MemorySegment m, double degrees) throws Throwable {
		return rotate(m, 0, degrees, 0);
	}

	public MemorySegment rotateZ(MemorySegment m, double degrees) throws Throwable {
		return rotate(m, 0, 0, degrees);
	}

	// ManifoldManifold* manifold_mirror(void* mem, ManifoldManifold* m, double nx,
	// double ny, double nz);
	public MemorySegment mirror(MemorySegment m, double nx, double ny, double nz) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_mirror").invoke(mem, m, nx, ny, nz);
	}

	// ===== Boolean operations =====

	// ManifoldManifold* manifold_union(void* mem, ManifoldManifold* a,
	// ManifoldManifold* b);
	public MemorySegment union(MemorySegment a, MemorySegment b) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_union").invoke(mem, a, b);
	}

	// ManifoldManifold* manifold_difference(void* mem, ManifoldManifold* a,
	// ManifoldManifold* b);
	public MemorySegment difference(MemorySegment a, MemorySegment b) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_difference").invoke(mem, a, b);
	}

	// ManifoldManifold* manifold_intersection(void* mem, ManifoldManifold* a,
	// ManifoldManifold* b);
	public MemorySegment intersection(MemorySegment a, MemorySegment b) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_intersection").invoke(mem, a, b);
	}

	// ManifoldManifold* manifold_boolean(void* mem, ManifoldManifold* a,
	// ManifoldManifold* b, ManifoldOpType op); OpType : char { Add, Subtract,
	// Intersect };
	public MemorySegment booleanOp(MemorySegment a, MemorySegment b, int opType) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_boolean").invoke(mem, a, b, opType);
	}

	// ManifoldManifold* manifold_batch_boolean(void* mem, ManifoldManifoldVec* ms,
	// ManifoldOpType op);
	public MemorySegment batchUnion(MemorySegment[] shapes) throws Throwable {
		MemorySegment vec = (MemorySegment) functions.get("manifold_alloc_manifold_vec").invoke();
		try {
			for (MemorySegment shape : shapes)
				functions.get("manifold_manifold_vec_push_back").invoke(vec, shape);

			MemorySegment resultMem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
			return (MemorySegment) functions.get("manifold_batch_boolean").invoke(resultMem, vec, OPTYPE_UNION);
		} finally {
			// Always cleanup vec, even if batch_boolean fails
			if (vec != null) {
				try {
					functions.get("manifold_delete_manifold_vec").invoke(vec);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	// ===== Info =====

	// size_t manifold_manifold_size(); Should return 16 bytes
	public long manifoldSize() throws Throwable {
		return (long) functions.get("manifold_manifold_size").invoke();
	}

	// size_t manifold_meshgl_size(); should return 232 bytes
	public long meshGLSize() throws Throwable {
		return (long) functions.get("manifold_meshgl_size").invoke();
	}

	// size_t manifold_meshgl64_size(); Should return 232
	public long meshGL64Size() throws Throwable {
		return (long) functions.get("manifold_meshgl64_size").invoke();
	}

	// size_t manifold_manifold_vec_size(); Should return 8
	public long vecSize() throws Throwable {
		return (long) functions.get("manifold_manifold_vec_size").invoke();
	}

	// double manifold_volume(ManifoldManifold* m);
	public double volume(MemorySegment m) throws Throwable {
		return (double) functions.get("manifold_volume").invoke(m);
	}

	// double manifold_surface_area(ManifoldManifold* m);
	public double surfaceArea(MemorySegment m) throws Throwable {
		return (double) functions.get("manifold_surface_area").invoke(m);
	}

	// double manifold_epsilon(ManifoldManifold* m);
	public double epsilon(MemorySegment m) throws Throwable {
		return (double) functions.get("manifold_epsilon").invoke(m);
	}

	// int manifold_genus(ManifoldManifold* m);
	public int genus(MemorySegment m) throws Throwable {
		return (int) functions.get("manifold_genus").invoke(m);
	}

	// size_t manifold_num_vert(ManifoldManifold* m);
	public long numVert(MemorySegment m) throws Throwable {
		return (long) functions.get("manifold_num_vert").invoke(m);
	}

	// size_t manifold_num_tri(ManifoldManifold* m);
	public long numTri(MemorySegment m) throws Throwable {
		return (long) functions.get("manifold_num_tri").invoke(m);
	}

	// size_t manifold_num_edge(ManifoldManifold* m);
	public long numEdge(MemorySegment m) throws Throwable {
		return (long) functions.get("manifold_num_edge").invoke(m);
	}

	// size_t manifold_num_prop(ManifoldManifold* m);
	public long numProp(MemorySegment m) throws Throwable {
		return (long) functions.get("manifold_num_prop").invoke(m);
	}

	// ===== Refine =====

	// ManifoldManifold* manifold_refine(void* mem, ManifoldManifold* m, int
	// refine); refine > 1
	public MemorySegment refine(MemorySegment m, int level) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_refine").invoke(mem, m, level);
	}

	// ManifoldManifold* manifold_refine_to_length(void* mem, ManifoldManifold* m,
	// double length);
	public MemorySegment refineToLength(MemorySegment m, double length) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_refine_to_length").invoke(mem, m, length);
	}

	// ManifoldManifold* manifold_refine_to_tolerance(void* mem, ManifoldManifold*
	// m, double tolerance);
	public MemorySegment refineToTolerance(MemorySegment m, double tolerance) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_refine_to_tolerance").invoke(mem, m, tolerance);
	}

	// ManifoldManifold* manifold_smooth_by_normals(void* mem, ManifoldManifold* m,
	public MemorySegment smoothByNormals(MemorySegment m, int normalIdx) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_smooth_by_normals").invoke(mem, m, normalIdx);
	}

	// ManifoldManifold* manifold_calculate_normals(void* mem, ManifoldManifold* m,
	// int normal_idx, double min_sharp_angle);
	public MemorySegment calculateNormals(MemorySegment m, int normalIdx, double minSharpAngle) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_calculate_normals").invoke(mem, m, normalIdx, minSharpAngle);
	}

	// ManifoldManifold* manifold_trim_by_plane(void* mem, ManifoldManifold* m,
	// double normal_x, double normal_y, double normal_z, double offset);
	public MemorySegment trimByPlane(MemorySegment m, double nx, double ny, double nz, double offset) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_trim_by_plane").invoke(mem, m, nx, ny, nz, offset);
	}

	// ManifoldManifold* manifold_hull(void* mem, ManifoldManifold* m);
	public MemorySegment hull(MemorySegment m) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_hull").invoke(mem, m);
	}

	// ManifoldManifold* manifold_batch_hull(void* mem, ManifoldManifoldVec* ms);
	public MemorySegment batchHull(MemorySegment[] shapes) throws Throwable {
		MemorySegment vec = (MemorySegment) functions.get("manifold_alloc_manifold_vec").invoke();
		try {
			for (MemorySegment shape : shapes)
				functions.get("manifold_manifold_vec_push_back").invoke(vec, shape);

			MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
			return (MemorySegment) functions.get("manifold_batch_hull").invoke(mem, vec);
		} finally {
			// Always cleanup vec, even if batch_hull fails
			if (vec != null) {
				try {
					functions.get("manifold_delete_manifold_vec").invoke(vec);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	// ManifoldManifold* manifold_compose(void* mem, ManifoldManifoldVec* ms);
	public MemorySegment compose(MemorySegment[] parts) throws Throwable {
		MemorySegment vec = (MemorySegment) functions.get("manifold_alloc_manifold_vec").invoke();
		try {
			for (MemorySegment part : parts)
				functions.get("manifold_manifold_vec_push_back").invoke(vec, part);

			MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
			return (MemorySegment) functions.get("manifold_compose").invoke(mem, vec);
		} finally {
			if (vec != null) {
				try {
					functions.get("manifold_delete_manifold_vec").invoke(vec);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	// ManifoldManifoldPair manifold_split(void* mem_first, void* mem_second,
	// ManifoldManifold* a, ManifoldManifold* b);
	public MemorySegment[] split(MemorySegment a, MemorySegment b) throws Throwable {
		MemorySegment firstMem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		MemorySegment secondMem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();

		try (Arena arena = Arena.ofConfined()) {
			functions.get("manifold_split").invoke(arena, firstMem, secondMem, a, b);
			return new MemorySegment[] { firstMem, secondMem };
		} catch (Throwable e) {
			// Clean up allocated manifolds on failure
			try {
				functions.get("manifold_delete_manifold").invoke(firstMem);
			} catch (Throwable ignored) {
			}

			try {
				functions.get("manifold_delete_manifold").invoke(secondMem);
			} catch (Throwable ignored) {
			}

			throw e;
		}
	}

	// Placeholder
	//	public MemorySegment[] decompose(MemorySegment m) throws Throwable {
	//		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold_vec").invoke();
	//		MemorySegment vec = (MemorySegment) functions.get("manifold_decompose").invoke(mem, m);
	// TODO: Needs vector access functions to implement
	//		return new MemorySegment[]{m}; 
	//	}

	// Creates an independent copy (safe for boolean operations)
	// ManifoldManifold* manifold_copy(void* mem, ManifoldManifold* m);
	public MemorySegment copy(MemorySegment m) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_copy").invoke(mem, m);
	}

	// Returns a fresh instance of the original input mesh (removes all transforms)
	public MemorySegment asOriginal(MemorySegment m) throws Throwable {
		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
		return (MemorySegment) functions.get("manifold_as_original").invoke(mem, m);
	}

	// ManifoldBox* manifold_bounding_box(void* mem, ManifoldManifold* m);
	public MemorySegment boundingBox(MemorySegment m) throws Throwable {
		MemorySegment box = (MemorySegment) functions.get("manifold_alloc_box").invoke();
		return (MemorySegment) functions.get("manifold_bounding_box").invoke(box, m);
	}

	public MemorySegment importMeshGL(float[] vertices, int[] triangles, long nVerts, long nTris) throws Throwable {
		System.out.println("Importing mesh: " + nVerts + " vertices, " + nTris + " triangles");

		// Validate input arrays match declared sizes
		if (vertices.length < nVerts * 3 || triangles.length < nTris * 3) {
			throw new IllegalArgumentException("Array length mismatch: vertices=" + vertices.length + " (expected "
					+ (nVerts * 3) + "), triangles=" + triangles.length + " (expected " + (nTris * 3) + ")");
		}

		MethodHandle mh = functions.get("manifold_meshgl");
		if (mh == null)
			throw new RuntimeException("manifold_meshgl not available in library");

		try (Arena tempArena = Arena.ofConfined()) {

			// Copy vertices
			MemorySegment vertPtr = tempArena.allocate(nVerts * 3 * Float.BYTES);
			vertPtr.copyFrom(MemorySegment.ofArray(vertices));

			// Copy triangles
			MemorySegment triPtr = tempArena.allocate(nTris * 3 * Integer.BYTES);
			triPtr.copyFrom(MemorySegment.ofArray(triangles));

			// Allocate MeshGL (native heap - must track for cleanup)
			MemorySegment meshGLmem = (MemorySegment) functions.get("manifold_alloc_meshgl").invoke();
			MemorySegment meshGL = null;

			try { // Create MeshGL

				meshGL = (MemorySegment) mh.invoke(meshGLmem, vertPtr, nVerts, 3L, triPtr, nTris);

				// Merge (native heap - must track for cleanup)
				MemorySegment mergedMem = (MemorySegment) functions.get("manifold_alloc_meshgl").invoke();
				MemorySegment merged = null;

				try {
					merged = (MemorySegment) functions.get("manifold_meshgl_merge").invoke(mergedMem, meshGL);

					// Create Manifold
					MemorySegment manMem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
					MemorySegment manifold = (MemorySegment) functions.get("manifold_of_meshgl").invoke(manMem, merged);

					// Success path: cleanup intermediates, return result
					try {
						functions.get("manifold_delete_meshgl").invoke(merged);
					} catch (Throwable ignored) {
					}

					try {
						functions.get("manifold_delete_meshgl").invoke(meshGL);
					} catch (Throwable ignored) {
					}

					return manifold;

				} catch (Throwable e) { // Cleanup merged on failure

					if (merged != null) {
						try {
							functions.get("manifold_delete_meshgl").invoke(merged);
						} catch (Throwable ignored) {
						}
					} else {
						try {
							functions.get("manifold_delete_meshgl").invoke(mergedMem);
						} catch (Throwable ignored) {
						}
					}
					throw e;
				}

			} catch (Throwable e) {// Cleanup meshGL on failure

				if (meshGL != null) {
					try {
						functions.get("manifold_delete_meshgl").invoke(meshGL);
					} catch (Throwable ignored) {
					}
				} else {
					try {
						functions.get("manifold_delete_meshgl").invoke(meshGLmem);
					} catch (Throwable ignored) {
					}
				}
				throw e;
			}

		} // tempArena closed here, vertPtr and triPtr freed automatically
	}

	public MeshData exportMeshGL(MemorySegment manifold) throws Throwable {

		MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_meshgl").invoke();
		MemorySegment meshGL = (MemorySegment) functions.get("manifold_get_meshgl").invoke(mem, manifold);

		try {
			long numVert = (long) functions.get("manifold_meshgl_num_vert").invoke(meshGL);
			long numTri = (long) functions.get("manifold_meshgl_num_tri").invoke(meshGL);
			long numProp = (long) functions.get("manifold_meshgl_num_prop").invoke(meshGL);

			float[] vertices = new float[(int) (numVert * 3)];
			int[] triangles = new int[(int) (numTri * 3)];

			// Use temporary confined arena for large temporary allocations
			try (Arena tempArena = Arena.ofConfined()) {
				if (numVert > 0) {
					long vertLen = (long) functions.get("manifold_meshgl_vert_properties_length").invoke(meshGL);
					MemorySegment tempMem = tempArena.allocate(vertLen * Float.BYTES);
					functions.get("manifold_meshgl_vert_properties").invoke(tempMem, meshGL);

					for (int i = 0; i < numVert; i++)
						for (int j = 0; (j < 3) && (j < numProp); j++)
							vertices[i * 3 + j] = tempMem.getAtIndex(ValueLayout.JAVA_FLOAT, i * numProp + j);
				}

				if (numTri > 0) {
					long triLen = (long) functions.get("manifold_meshgl_tri_length").invoke(meshGL);
					MemorySegment tempMem = tempArena.allocate(triLen * Integer.BYTES);
					functions.get("manifold_meshgl_tri_verts").invoke(tempMem, meshGL);

					for (int i = 0; i < triLen; i++)
						triangles[i] = tempMem.getAtIndex(ValueLayout.JAVA_INT, i);
				}
			} // tempArena closed here, memory freed automatically

			return new MeshData(vertices, triangles, (int) numVert, (int) numTri);

		} finally {
			// Guaranteed cleanup even if exceptions occur above
			try {
				functions.get("manifold_delete_meshgl").invoke(meshGL);
			} catch (Throwable ignored) {
			}
		}
	}

	public javafx.geometry.BoundingBox getJavaFXBounds(MemorySegment m) {

		MemorySegment box = null;
		try (Arena arena = Arena.ofConfined()) { // Arena needed for struct returns
			box = (MemorySegment) functions.get("manifold_alloc_box").invoke();
			functions.get("manifold_bounding_box").invoke(box, m);

			MemorySegment min = (MemorySegment) functions.get("manifold_box_min").invoke(arena, box);
			MemorySegment max = (MemorySegment) functions.get("manifold_box_max").invoke(arena, box);

			double minX = min.get(ValueLayout.JAVA_DOUBLE, 0);
			double minY = min.get(ValueLayout.JAVA_DOUBLE, 8);
			double minZ = min.get(ValueLayout.JAVA_DOUBLE, 16);
			double maxX = max.get(ValueLayout.JAVA_DOUBLE, 0);
			double maxY = max.get(ValueLayout.JAVA_DOUBLE, 8);
			double maxZ = max.get(ValueLayout.JAVA_DOUBLE, 16);

			return new javafx.geometry.BoundingBox(minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ);

		} catch (Throwable e) {
			e.printStackTrace();
			return new javafx.geometry.BoundingBox(0, 0, 0, 0, 0, 0);

		} finally {
			if (box != null) {
				try {
					functions.get("manifold_delete_box").invoke(box);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	// Returns dimensions as double[3] = {width, height, depth}
	public double[] boxDimensions(MemorySegment box) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment dims = (MemorySegment) functions.get("manifold_box_dimensions").invoke(arena, box);
			return new double[] { dims.get(ValueLayout.JAVA_DOUBLE, 0), // width (x)
					dims.get(ValueLayout.JAVA_DOUBLE, 8), // height (y)
					dims.get(ValueLayout.JAVA_DOUBLE, 16) // depth (z)
			};
		}
	}

	public javafx.geometry.BoundingBox getBounds(MemorySegment m) throws Throwable {
		MemorySegment box = (MemorySegment) functions.get("manifold_alloc_box").invoke();
		try {
			functions.get("manifold_bounding_box").invoke(box, m);

			// Create local arena for the struct return allocation
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment dims = (MemorySegment) functions.get("manifold_box_dimensions").invoke(arena, box);
				double w = dims.get(ValueLayout.JAVA_DOUBLE, 0);
				double h = dims.get(ValueLayout.JAVA_DOUBLE, 8);
				double d = dims.get(ValueLayout.JAVA_DOUBLE, 16);
				return new javafx.geometry.BoundingBox(0, 0, 0, w, h, d);
			}
		} finally {
			functions.get("manifold_delete_box").invoke(box);
		}
	}

	// Center an object around the origine
	public MemorySegment centerObject(MemorySegment m) throws Throwable {
		MemorySegment box = null;
		try {
			box = (MemorySegment) functions.get("manifold_alloc_box").invoke();
			functions.get("manifold_bounding_box").invoke(box, m);

			// Create local arena for struct return allocation
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment center = (MemorySegment) functions.get("manifold_box_center").invoke(arena, box);
				double cx = center.get(ValueLayout.JAVA_DOUBLE, 0);
				double cy = center.get(ValueLayout.JAVA_DOUBLE, 8);
				double cz = center.get(ValueLayout.JAVA_DOUBLE, 16);

				return translate(m, -cx, -cy, -cz);
			}
		} finally {
			if (box != null) {
				try {
					functions.get("manifold_delete_box").invoke(box);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	public long estimateMemoryBytes(MemorySegment m) throws Throwable {
		long nVert = numVert(m);
		long nTri = numTri(m);
		long nEdge = numEdge(m);
		long nProp = numProp(m);

		// Internal Half-Edge Mesh Structure (Double Precision):
		// - Vertex positions: vec3 (3 doubles) = 24 bytes per vertex
		// - Half-edge records: ~40 bytes each (twin/next/vert/face/edge as int64_t +
		// padding)
		// - Face records: ~8 bytes each (int64_t index to one half-edge)
		// - Edge records: ~8 bytes each (int64_t index to one half-edge)
		long vertexData = nVert * 24L; // vec3 double positions (3 × 8 bytes)
		long halfedgeData = nEdge * 40L; // Half-edge struct with int64_t indices
		long faceData = nTri * 8L; // Face to half-edge reference (int64_t)
		long edgeData = nEdge * 8L; // Edge to half-edge reference (int64_t)

		// Property storage (if present): nProp double channels per vertex
		// Properties include normal(3), UV(2), color(4), etc. - all doubles
		long propertyData = nVert * nProp * 8L;

		// MeshGL64 export representation (double precision):
		// - Vertices: nVert × nProp × 8 bytes (double properties)
		// - Indices: nTri × 3 × 8 bytes (int64_t indices)
		long meshglVertexData = nVert * Math.max(3, nProp) * 8L; // At least position (3 doubles)
		long meshglIndexData = nTri * 3L * 8L; // int64_t triangle indices
		long meshglStruct = meshGLSize(); // sizeof(MeshGL64) header struct

		// Conservative total: internal structure + export representation
		long internalTotal = vertexData + halfedgeData + faceData + edgeData + propertyData;
		long exportTotal = meshglStruct + meshglVertexData + meshglIndexData;
		long manifoldHandle = manifoldSize(); // Pointer/handle overhead

		// Add 25% overhead for BVH acceleration structures and alignment padding
		long bvhOverhead = (long) (internalTotal * 0.25);

		return manifoldHandle + internalTotal + exportTotal + bvhOverhead;
	}

	public void delete(MemorySegment manifold) throws Throwable {
		functions.get("manifold_delete_manifold").invoke(manifold);
	}

	// Null-safe delete
	public void safeDelete(MemorySegment m) {
		if (m == null)
			return;
		try {
			delete(m);
		} catch (Throwable t) {
			// Already freed or invalid
		}
	}

	public void close() {
	}

	public static boolean isNativeLibraryLoaded() {
		return loaded;
	}

	public record MeshData(float[] vertices, int[] triangles, int vertCount, int triCount) {
	}
}