package com.cadoodlecad.manifold;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.io.*;
import java.lang.foreign.MemorySegment;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.*;
import java.io.*;
import java.lang.foreign.MemorySegment;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.*;

public class ManifoldBindings {
	public enum ManifoldError {
		NO_ERROR, NON_FINITE_VERTEX, NOT_MANIFOLD, VERTEX_INDEX_OUT_OF_BOUNDS, PROPERTIES_WRONG_LENGTH,
		MISSING_POSITION_PROPERTIES, MERGE_VECTORS_DIFFERENT_LENGTHS, MERGE_INDEX_OUT_OF_BOUNDS, TRANSFORM_WRONG_LENGTH,
		RUN_INDEX_WRONG_LENGTH, FACE_ID_WRONG_LENGTH, INVALID_CONSTRUCTION, RESULT_TOO_LARGE;

		public static ManifoldError fromInt(int code) {
			ManifoldError[] values = values();
			if (code < 0 || code >= values.length)
				throw new IllegalArgumentException("Unknown ManifoldError code: " + code);
			return values[code];
		}
	}

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

			// Check timestamp, copy only newer
			boolean needsCopy = !libFile.exists();
			if (needsCopy) {

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
		// loadNativeLibrary("libmeshIO", cacheDirectory);
	}

	public ManifoldBindings() throws Exception {
		this((File) null);
	}

	public ManifoldBindings(File cacheDirectory) throws Exception {
		if (!isNativeLibraryLoaded()) {
			loadNativeLibrarys(cacheDirectory);
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


		load("manifold_alloc_polygons", ValueLayout.ADDRESS);
		// size_t manifold_polygons_length(ManifoldPolygons* ps);
		load("manifold_polygons_length", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

		// size_t manifold_polygons_simple_length(ManifoldPolygons* ps, size_t idx);
		load("manifold_polygons_simple_length", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
		// ManifoldVec2 manifold_polygons_get_point(ManifoldPolygons* ps, size_t simple_idx, size_t pt_idx);
		// ManifoldVec2 = {double x, double y} = 16 bytes, returned by value
		load("manifold_polygons_get_point", MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE),
				ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
		// ManifoldPolygons* manifold_alloc_polygons();
		load("manifold_alloc_polygons", ValueLayout.ADDRESS);

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
		//load("manifold_manifold_vec_size", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
		// REPLACE WITH (no argument — it's a sizeof-style function):
		load("manifold_manifold_vec_size", ValueLayout.JAVA_LONG);
		// AND add the length function for actual runtime vec count:
		load("manifold_manifold_vec_length", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
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

		load("manifold_hull_pts", ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

		System.out.println("Available Manifold functions: " + functions.keySet());

	}

	// ManifoldError manifold_status(ManifoldManifold* m);
	public ManifoldError status(MemorySegment m) throws Throwable {
		int code = (int) functions.get("manifold_status").invoke(m);
		return ManifoldError.fromInt(code);
	}

	public ArrayList<double[][]> slice(MemorySegment m, double height) throws Throwable {
		MemorySegment polygons = null;
		try (Arena arena = Arena.ofConfined()) {
			// manifold_slice(void* mem, ManifoldManifold* m, double height) → ManifoldPolygons*
			MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_polygons").invoke();
			polygons = (MemorySegment) functions.get("manifold_slice").invoke(mem, m, height);

			// size_t manifold_polygons_length(ManifoldPolygons* ps) → number of contours
			long numContours = (long) functions.get("manifold_polygons_length").invoke(polygons);
			ArrayList<double[][]> result = new ArrayList<>((int) numContours);

			for (int c = 0; c < numContours; c++) {
				// size_t manifold_polygons_simple_length(ManifoldPolygons* ps, size_t idx)
				long len = (long) functions.get("manifold_polygons_simple_length").invoke(polygons, (long) c);
				double[][] contour = new double[(int) len][2];

				for (int i = 0; i < len; i++) {
					// ManifoldVec2 manifold_polygons_get_point(ManifoldPolygons* ps, size_t simple_idx, size_t pt_idx)
					MemorySegment pt = (MemorySegment) functions.get("manifold_polygons_get_point").invoke(arena,
							polygons, (long) c, (long) i);
					contour[i][0] = pt.get(ValueLayout.JAVA_DOUBLE, 0); // x
					contour[i][1] = pt.get(ValueLayout.JAVA_DOUBLE, 8); // y
				}
				result.add(contour);
			}
			return result;
		} finally {
			if (polygons != null) {
				try {
					functions.get("manifold_delete_polygons").invoke(polygons);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	private void deleteMeshGL64(MemorySegment seg) {
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

				try {
					functions.get("manifold_meshgl64_merge").invoke(mergedMem, meshGL);

					MemorySegment manMem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
					MemorySegment result = (MemorySegment) functions.get("manifold_of_meshgl64").invoke(manMem, mergedMem);

					try {
						functions.get("manifold_delete_meshgl64").invoke(mergedMem);
						mergedMem=null;
					} catch (Throwable ignored) {
					}

					return result;

				} catch (Throwable e) {
					if (mergedMem != null)
						try {
							functions.get("manifold_delete_meshgl64").invoke(mergedMem);
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

	/**
	 * Computes the convex hull of a set of points.
	 *
	 * Each point is supplied as a {@code double[3]} of {x, y, z}. If fewer than
	 * 4 points are provided, or all points are coplanar, an empty manifold is
	 * returned (matching the C++ API's documented behaviour).
	 *
	 * The caller owns the returned MemorySegment and must eventually pass it
	 * to {@link #delete(MemorySegment)} or {@link #safeDelete(MemorySegment)}.
	 *
	 * @param points list of points, each a double[3] of {x, y, z}
	 * @return the convex hull as a ManifoldManifold* MemorySegment
	 * @throws IllegalArgumentException if any point array has length != 3
	 * @throws Throwable on native call failure
	 */
	public MemorySegment hull(ArrayList<double[]> points) throws Throwable {
		if (points == null || points.isEmpty()) {
			return empty();
		}

		// Validate all points up front before allocating any native memory.
		for (int i = 0; i < points.size(); i++) {
			if (points.get(i) == null || points.get(i).length != 3) {
				throw new IllegalArgumentException("Point at index " + i + " must be a double[3] of {x, y, z}");
			}
		}

		// ManifoldVec3 is three packed doubles: {double x, double y, double z} = 24 bytes each.
		final long VEC3_BYTES = 3 * Double.BYTES;
		long count = points.size();

		try (Arena arena = Arena.ofConfined()) {
			// Allocate a flat array of ManifoldVec3 structs in a temporary arena.
			MemorySegment ptsBuffer = arena.allocate(VEC3_BYTES * count);
			for (int i = 0; i < count; i++) {
				double[] pt = points.get(i);
				long base = i * VEC3_BYTES;
				ptsBuffer.set(ValueLayout.JAVA_DOUBLE, base, pt[0]); // x
				ptsBuffer.set(ValueLayout.JAVA_DOUBLE, base + Double.BYTES, pt[1]); // y
				ptsBuffer.set(ValueLayout.JAVA_DOUBLE, base + 2 * Double.BYTES, pt[2]); // z
			}

			MemorySegment mem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
			return (MemorySegment) functions.get("manifold_hull_pts").invoke(mem, ptsBuffer, count);
		}
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
				delete(firstMem);
			} catch (Throwable ignored) {
				ignored.printStackTrace();
			}

			try {
				delete(secondMem);
			} catch (Throwable ignored) {
				ignored.printStackTrace();
			}

			throw e;
		}
	}


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

		if (vertices.length < nVerts * 3 || triangles.length < nTris * 3) {
			throw new IllegalArgumentException("Array length mismatch");
		}

		MethodHandle mh = functions.get("manifold_meshgl");
		if (mh == null)
			throw new RuntimeException("manifold_meshgl not available in library");

		try (Arena tempArena = Arena.ofConfined()) {

			MemorySegment vertPtr = tempArena.allocate(nVerts * 3 * Float.BYTES);
			vertPtr.copyFrom(MemorySegment.ofArray(vertices));

			MemorySegment triPtr = tempArena.allocate(nTris * 3 * Integer.BYTES);
			triPtr.copyFrom(MemorySegment.ofArray(triangles));

			// Allocate both up front so cleanup is uniform
			MemorySegment meshGLMem = (MemorySegment) functions.get("manifold_alloc_meshgl").invoke();

			MemorySegment mergedMem = (MemorySegment) functions.get("manifold_alloc_meshgl").invoke();
			MemorySegment merged = null;
			MemorySegment meshGL = null;
			MemorySegment manMem = null;
			MemorySegment manifold = null;
			try {
				// Both of these likely return the same pointer they were given
				meshGL = (MemorySegment) mh.invoke(meshGLMem, vertPtr, nVerts, 3L, triPtr, nTris);
				merged = (MemorySegment) functions.get("manifold_meshgl_merge").invoke(mergedMem, meshGL);

				manMem = (MemorySegment) functions.get("manifold_alloc_manifold").invoke();
				manifold = (MemorySegment) functions.get("manifold_of_meshgl").invoke(manMem, merged);
				//				System.out.println("meshGLMem addr: " + meshGLMem.address());
				//				System.out.println("meshGL    addr: " + meshGL.address());
				//				System.out.println("mergedMem addr: " + mergedMem.address());
				//				System.out.println("merged    addr: " + merged.address());
				//				System.out.println("manMem    addr: " + manMem.address());
				//				System.out.println("manifold  addr: " + manifold.address());
				return manifold;

			} finally {
				// Collect unique addresses to delete
				Set<Long> freed = new HashSet<>();
				for (MemorySegment seg : new MemorySegment[] { merged, meshGL }) {
					if (seg == null)
						continue;
					if (seg.address() == manifold.address())
						continue;
					if (freed.add(seg.address())) { // add() returns false if already present
						//System.out.print("\nFreeing: " + seg.address());
						functions.get("manifold_delete_meshgl").invoke(seg);
						//System.out.print(" done\n ");

					}
				}
				freed.clear();
			}
		}
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

	//private HashMap<MemorySegment,Exception> deleted = new HashMap<>();

	public void delete(MemorySegment manifold) throws Throwable {
		//		if (deleted.containsKey(manifold)) {
		//			System.err.println("Memory was deleted at ");
		//			deleted.get(manifold).printStackTrace();
		//			throw new DoubleFreeException();
		//		}
		functions.get("manifold_delete_manifold").invoke(manifold);
		//deleted.put(manifold, new Exception());
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


	/**
	 * Lightweight, zero-dependency library for reading and writing STL and 3MF mesh files,
	 * integrated directly with {@link ManifoldBindings}.
	 *
	 * <p>Instantiate with a {@link ManifoldBindings} reference so that export methods can
	 * extract mesh data from a live {@link MemorySegment}, and import methods can populate
	 * a new {@link MemorySegment} from a file.
	 *
	 * <h2>Export (MemorySegment → File)</h2>
	 * <pre>{@code
	 * MeshIO io = new MeshIO(bindings);
	 *
	 * // STL — single mesh
	 * io.exportSTL(manifoldSegment, new File("out.stl"));
	 *
	 * // 3MF — one or many meshes, each becomes a separate <object> in the file
	 * ArrayList<MemorySegment> meshes = new ArrayList<>();
	 * meshes.add(bodySegment);
	 * meshes.add(lidSegment);
	 * io.export3MF(meshes, new File("assembly.3mf"));
	 * }</pre>
	 *
	 * <h2>Import (File → MemorySegment)</h2>
	 * <pre>{@code
	 * MeshIO io = new MeshIO(bindings);
	 *
	 * // STL — always one mesh
	 * MemorySegment manifold = io.importSTL(new File("model.stl"));
	 *
	 * // 3MF — one MemorySegment per <object> in the file
	 * ArrayList<MemorySegment> meshes = io.import3MF(new File("assembly.3mf"));
	 * }</pre>
	 *
	 * <h2>STL notes</h2>
	 * <ul>
	 *   <li>Reads both binary and ASCII STL.</li>
	 *   <li>Writes binary STL (compact, universally supported).</li>
	 *   <li>STL is an unindexed format; duplicate vertices are welded automatically inside
	 *       {@link ManifoldBindings#importMeshGL} via {@code manifold_meshgl_merge}.</li>
	 *   <li>Face normals on export are computed from vertex positions (right-hand rule).</li>
	 * </ul>
	 *
	 * <h2>3MF notes</h2>
	 * <ul>
	 *   <li>Reads and writes 3MF Core Specification 1.x (a ZIP archive containing
	 *       {@code 3D/3dmodel.model}).</li>
	 *   <li>Each {@code <object>} in the file maps to exactly one {@link MemorySegment}.</li>
	 *   <li>Indexed — vertex topology is fully preserved on round-trip.</li>
	 *   <li>Preferred format when manifoldness must survive a save/load cycle.</li>
	 * </ul>
	 */


	// =========================================================================
	// Export API  (MemorySegment → File)
	// =========================================================================

	/**
	 * Exports the manifold represented by {@code manifold} to a binary STL file.
	 *
	 * <p>Calls {@link ManifoldBindings#exportMeshGL(MemorySegment)} to extract mesh data
	 * from the native segment, then writes it as a binary STL.
	 *
	 * @param manifold a fully initialised manifold {@link MemorySegment}
	 * @param file     destination file (created or overwritten)
	 */
	public void exportSTL(MemorySegment manifold, File file) throws Throwable {
		ManifoldBindings.MeshData mesh = this.exportMeshGL(manifold);
		writeBinarySTL(mesh.vertices(), mesh.triangles(), mesh.vertCount(), mesh.triCount(), file);
	}

	/**
	 * Exports one or more manifolds to a single 3MF file.
	 *
	 * <p>Each {@link MemorySegment} in {@code manifolds} becomes a separate
	 * {@code <object>} element (with its own {@code <vertices>} and {@code <triangles>}
	 * blocks) and a corresponding {@code <item>} reference in the {@code <build>} section.
	 * Slicers and CAD tools that support multi-body 3MF will load each object independently.
	 *
	 * <p>Calls {@link ManifoldBindings#exportMeshGL(MemorySegment)} for each segment to
	 * extract mesh data before writing.
	 *
	 * @param manifolds one or more fully initialised manifold {@link MemorySegment}s
	 * @param file      destination file (created or overwritten)
	 * @throws IllegalArgumentException if {@code manifolds} is empty
	 */
	public void export3MF(ArrayList<MemorySegment> manifolds, File file) throws Throwable {
		if (manifolds == null || manifolds.isEmpty())
			throw new IllegalArgumentException("manifolds list must not be empty");

		List<ManifoldBindings.MeshData> meshes = new ArrayList<>(manifolds.size());
		for (MemorySegment seg : manifolds)
			meshes.add(this.exportMeshGL(seg));

		write3MFInternal(meshes, file);
	}

	// =========================================================================
	// Import API  (File → MemorySegment)
	// =========================================================================

	/**
	 * Reads an STL file (binary or ASCII) and imports it into a new manifold.
	 *
	 * <p>Parses the file into vertex and triangle arrays, then delegates to
	 * {@link ManifoldBindings#importMeshGL(float[], int[], long, long)}, which welds
	 * duplicate vertices via {@code manifold_meshgl_merge} and constructs a valid manifold.
	 *
	 * @param file a valid STL file (binary or ASCII)
	 * @return a fully initialised manifold {@link MemorySegment} ready for use with
	 *         any {@link ManifoldBindings} operation
	 */
	public MemorySegment importSTL(File file) throws Throwable {
		RawMesh raw = parseSTL(file);
		return this.importMeshGL(raw.vertices, raw.triangles, raw.vertices.length / 3L, raw.triangles.length / 3L);
	}

	/**
	 * Reads a 3MF file and imports each {@code <object>} as a separate manifold.
	 *
	 * <p>Returns one {@link MemorySegment} per {@code <object>} element found in the file,
	 * in document order. Each segment is independently constructed via
	 * {@link ManifoldBindings#importMeshGL(float[], int[], long, long)} so that vertex
	 * welding is applied per-object and each resulting manifold is self-contained.
	 *
	 * @param file a valid 3MF file
	 * @return one fully initialised manifold {@link MemorySegment} per object in the file;
	 *         never empty (throws if the file contains no objects)
	 */
	public ArrayList<MemorySegment> import3MF(File file) throws Throwable {
		List<RawMesh> objects = parse3MF(file);
		ArrayList<MemorySegment> result = new ArrayList<>(objects.size());
		for (RawMesh raw : objects)
			result.add(this.importMeshGL(raw.vertices, raw.triangles, raw.vertices.length / 3L,
					raw.triangles.length / 3L));
		return result;
	}

	// =========================================================================
	// STL – format detection
	// =========================================================================

	/**
	 * Heuristic: a file is ASCII STL if it starts with "solid" AND its size does not
	 * fit the exact binary STL formula {@code 80 + 4 + triCount * 50}.
	 * Some binary exporters also write "solid" in the header, so the size check is essential.
	 */
	private static boolean looksLikeAsciiSTL(byte[] header80, long fileSize) {
		String prefix = new String(header80, 0, Math.min(5, header80.length), StandardCharsets.US_ASCII).trim()
				.toLowerCase();
		if (!prefix.startsWith("solid"))
			return false; // definitely binary
		if (fileSize < 134)
			return true; // too small for valid binary

		// Binary size formula: 84 + N*50.  If remainder ≠ 0 it can't be binary.
		return (fileSize - 84) % 50 != 0;
	}

	// =========================================================================
	// STL – binary read
	// =========================================================================

	private static RawMesh readBinarySTL(File file) throws IOException {
		try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {

			dis.skipNBytes(80); // skip header text
			int triCount = Integer.reverseBytes(dis.readInt()); // little-endian uint32

			// Binary STL is unindexed: each facet has 3 independent vertices.
			// We emit a sequential index array; importMeshGL → merge will weld duplicates.
			float[] verts = new float[triCount * 9]; // 3 verts × 3 floats
			int[] tris = new int[triCount * 3];

			byte[] buf = new byte[50];
			ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

			for (int i = 0; i < triCount; i++) {
				dis.readFully(buf);
				bb.rewind();
				bb.position(12); // skip 3-float normal
				int base = i * 9;
				verts[base] = bb.getFloat();
				verts[base + 1] = bb.getFloat();
				verts[base + 2] = bb.getFloat();
				verts[base + 3] = bb.getFloat();
				verts[base + 4] = bb.getFloat();
				verts[base + 5] = bb.getFloat();
				verts[base + 6] = bb.getFloat();
				verts[base + 7] = bb.getFloat();
				verts[base + 8] = bb.getFloat();
				tris[i * 3] = i * 3;
				tris[i * 3 + 1] = i * 3 + 1;
				tris[i * 3 + 2] = i * 3 + 2;
			}
			return new RawMesh(verts, tris);
		}
	}

	// =========================================================================
	// STL – ASCII read
	// =========================================================================

	private static RawMesh readAsciiSTL(File file) throws IOException {
		List<Float> vList = new ArrayList<>();
		List<Integer> tList = new ArrayList<>();
		int vertIdx = 0;

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			String line;
			int faceStart = -1;
			while ((line = br.readLine()) != null) {
				line = line.trim().toLowerCase();
				if (line.startsWith("facet normal")) {
					faceStart = vertIdx;
				} else if (line.startsWith("vertex ")) {
					String[] parts = line.split("\\s+");
					vList.add(Float.parseFloat(parts[1]));
					vList.add(Float.parseFloat(parts[2]));
					vList.add(Float.parseFloat(parts[3]));
					vertIdx++;
				} else if (line.startsWith("endfacet")) {
					tList.add(faceStart);
					tList.add(faceStart + 1);
					tList.add(faceStart + 2);
				}
			}
		}

		float[] verts = new float[vList.size()];
		for (int i = 0; i < vList.size(); i++)
			verts[i] = vList.get(i);
		int[] tris = new int[tList.size()];
		for (int i = 0; i < tList.size(); i++)
			tris[i] = tList.get(i);
		return new RawMesh(verts, tris);
	}

	// =========================================================================
	// STL – combined entry point
	// =========================================================================

	private static RawMesh parseSTL(File file) throws IOException {
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] header = fis.readNBytes(80);
			return looksLikeAsciiSTL(header, file.length()) ? readAsciiSTL(file) : readBinarySTL(file);
		}
	}

	// =========================================================================
	// STL – binary write
	// =========================================================================

	private static void writeBinarySTL(float[] verts, int[] tris, int vertCount, int triCount, File file)
			throws IOException {
		// Layout: 80-byte header | 4-byte tri count | triCount × 50-byte records
		ByteBuffer buf = ByteBuffer.allocate(84 + triCount * 50).order(ByteOrder.LITTLE_ENDIAN);

		byte[] header = new byte[80];
		byte[] tag = "Manifold3D-Java MeshIO STL Export".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(tag, 0, header, 0, Math.min(tag.length, 80));
		buf.put(header);
		buf.putInt(triCount);

		for (int i = 0; i < triCount; i++) {
			int i0 = tris[i * 3] * 3;
			int i1 = tris[i * 3 + 1] * 3;
			int i2 = tris[i * 3 + 2] * 3;

			float ax = verts[i0], ay = verts[i0 + 1], az = verts[i0 + 2];
			float bx = verts[i1], by = verts[i1 + 1], bz = verts[i1 + 2];
			float cx = verts[i2], cy = verts[i2 + 1], cz = verts[i2 + 2];

			// Normal = (b−a) × (c−a), normalised
			float ux = bx - ax, uy = by - ay, uz = bz - az;
			float vx = cx - ax, vy = cy - ay, vz = cz - az;
			float nx = uy * vz - uz * vy;
			float ny = uz * vx - ux * vz;
			float nz = ux * vy - uy * vx;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 1e-12f) {
				nx /= len;
				ny /= len;
				nz /= len;
			}

			buf.putFloat(nx);
			buf.putFloat(ny);
			buf.putFloat(nz);
			buf.putFloat(ax);
			buf.putFloat(ay);
			buf.putFloat(az);
			buf.putFloat(bx);
			buf.putFloat(by);
			buf.putFloat(bz);
			buf.putFloat(cx);
			buf.putFloat(cy);
			buf.putFloat(cz);
			buf.putShort((short) 0); // attribute byte count
		}

		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(buf.array());
		}
	}

	// =========================================================================
	// 3MF – read
	// =========================================================================

	private static List<RawMesh> parse3MF(File file) throws IOException {
		return parseModelXml(extract3DModel(file));
	}

	/** Extracts the {@code 3D/3dmodel.model} entry text from the ZIP archive. */
	private static String extract3DModel(File file) throws IOException {
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().replace('\\', '/').equalsIgnoreCase("3D/3dmodel.model")) {
					return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
				}
				zis.closeEntry();
			}
		}
		throw new IOException("No 3D/3dmodel.model entry found in 3MF archive: " + file);
	}

	/**
	 * Hand-rolled XML pull parser for the 3MF model document.
	 *
	 * <p>Returns one {@link RawMesh} per {@code <object>} element, preserving the
	 * per-object vertex/triangle separation that the 3MF format encodes. Triangle indices
	 * within each object are local (start at 0), which is correct because each
	 * {@link RawMesh} is independently imported via {@code importMeshGL}.
	 */
	private static List<RawMesh> parseModelXml(String xml) {
		List<RawMesh> result = new ArrayList<>();
		List<Float> verts = null;
		List<Integer> tris = null;
		int pos = 0;
		int len = xml.length();

		while (pos < len) {
			int tagStart = xml.indexOf('<', pos);
			if (tagStart < 0)
				break;
			int tagEnd = xml.indexOf('>', tagStart);
			if (tagEnd < 0)
				break;

			String tag = xml.substring(tagStart + 1, tagEnd).trim();
			pos = tagEnd + 1;

			if (tag.startsWith("object") && !tag.startsWith("/object")) {
				// Opening <object> — start collecting for a new mesh
				verts = new ArrayList<>();
				tris = new ArrayList<>();
			} else if (tag.equals("/object")) {
				// Closing </object> — finalise and store the mesh
				if (verts != null && tris != null && !verts.isEmpty()) {
					float[] va = new float[verts.size()];
					for (int i = 0; i < verts.size(); i++)
						va[i] = verts.get(i);
					int[] ta = new int[tris.size()];
					for (int i = 0; i < tris.size(); i++)
						ta[i] = tris.get(i);
					result.add(new RawMesh(va, ta));
				}
				verts = null;
				tris = null;
			} else if (verts != null && tag.startsWith("vertex") && !tag.startsWith("vertices")) {
				// <vertex x=… y=… z=…/> — indices are local to this object
				verts.add(attrFloat(tag, "x"));
				verts.add(attrFloat(tag, "y"));
				verts.add(attrFloat(tag, "z"));
			} else if (tris != null && tag.startsWith("triangle") && !tag.startsWith("triangles")) {
				// <triangle v1=… v2=… v3=…/> — local indices, no offset needed
				tris.add(attrInt(tag, "v1"));
				tris.add(attrInt(tag, "v2"));
				tris.add(attrInt(tag, "v3"));
			}
		}

		if (result.isEmpty())
			throw new IllegalArgumentException("3MF file contains no <object> elements with geometry");
		return result;
	}

	// =========================================================================
	// 3MF – write
	// =========================================================================

	private static void write3MFInternal(List<ManifoldBindings.MeshData> meshes, File file) throws IOException {
		writeZip3MF(file, build3MFModelXml(meshes));
	}

	/**
	 * Builds the {@code 3dmodel.model} XML for one or more meshes.
	 *
	 * <p>Each mesh becomes a separate {@code <object id="N">} element (1-based).
	 * All objects are referenced in the {@code <build>} section so that slicers
	 * treat them as independent bodies within the same file.
	 */
	private static byte[] build3MFModelXml(List<ManifoldBindings.MeshData> meshes) {
		// Estimate capacity: fixed overhead + per-mesh overhead + per-vert/tri lines
		int cap = 512;
		for (ManifoldBindings.MeshData m : meshes)
			cap += 200 + m.vertCount() * 60 + m.triCount() * 55;
		StringBuilder sb = new StringBuilder(cap);

		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<model unit=\"millimeter\" xml:lang=\"en-US\"\n");
		sb.append("  xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n");
		sb.append("  <resources>\n");

		for (int objIdx = 0; objIdx < meshes.size(); objIdx++) {
			ManifoldBindings.MeshData mesh = meshes.get(objIdx);
			int objectId = objIdx + 1; // 3MF object IDs are 1-based
			float[] verts = mesh.vertices();
			int[] tris = mesh.triangles();

			sb.append("    <object id=\"").append(objectId).append("\" type=\"model\">\n");
			sb.append("      <mesh>\n");
			sb.append("        <vertices>\n");
			for (int i = 0; i < mesh.vertCount(); i++) {
				sb.append("          <vertex x=\"").append(verts[i * 3]).append("\" y=\"").append(verts[i * 3 + 1])
						.append("\" z=\"").append(verts[i * 3 + 2]).append("\"/>\n");
			}
			sb.append("        </vertices>\n");
			sb.append("        <triangles>\n");
			for (int i = 0; i < mesh.triCount(); i++) {
				sb.append("          <triangle v1=\"").append(tris[i * 3]).append("\" v2=\"").append(tris[i * 3 + 1])
						.append("\" v3=\"").append(tris[i * 3 + 2]).append("\"/>\n");
			}
			sb.append("        </triangles>\n");
			sb.append("      </mesh>\n");
			sb.append("    </object>\n");
		}

		sb.append("  </resources>\n");
		sb.append("  <build>\n");
		for (int objIdx = 0; objIdx < meshes.size(); objIdx++) {
			sb.append("    <item objectid=\"").append(objIdx + 1).append("\"/>\n");
		}
		sb.append("  </build>\n");
		sb.append("</model>\n");

		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	// =========================================================================
	// 3MF – ZIP container writer
	// =========================================================================

	private static void writeZip3MF(File file, byte[] modelBytes) throws IOException {
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
			zos.setMethod(ZipOutputStream.DEFLATED);
			zos.setLevel(Deflater.BEST_SPEED);

			// [Content_Types].xml — required by the OPC container spec
			putZipEntry(zos, "[Content_Types].xml",
					("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
							+ "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n"
							+ "  <Default Extension=\"rels\""
							+ " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
							+ "  <Default Extension=\"model\""
							+ " ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/>\n"
							+ "</Types>\n").getBytes(StandardCharsets.UTF_8));

			// _rels/.rels — maps the package root to the 3D model entry
			putZipEntry(zos, "_rels/.rels", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<Relationships"
					+ " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" + "  <Relationship"
					+ " Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\""
					+ " Target=\"/3D/3dmodel.model\" Id=\"rel0\"/>\n" + "</Relationships>\n")
							.getBytes(StandardCharsets.UTF_8));

			putZipEntry(zos, "3D/3dmodel.model", modelBytes);
		}
	}

	private static void putZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
		zos.putNextEntry(new ZipEntry(name));
		zos.write(data);
		zos.closeEntry();
	}

	// =========================================================================
	// XML attribute helpers — no DOM/SAX dependency
	// =========================================================================

	private static float attrFloat(String tag, String attr) {
		return Float.parseFloat(attrString(tag, attr));
	}

	private static int attrInt(String tag, String attr) {
		return Integer.parseInt(attrString(tag, attr));
	}

	/**
	 * Extracts a named XML attribute value from a raw tag string.
	 * Handles both {@code attr="value"} and {@code attr='value'} quoting styles.
	 */
	private static String attrString(String tag, String attr) {
		int idx = tag.indexOf(attr + "=");
		if (idx < 0)
			throw new IllegalArgumentException("Attribute '" + attr + "' not found in tag: " + tag);

		int valStart = idx + attr.length() + 1;
		char quote = tag.charAt(valStart);
		if (quote != '"' && quote != '\'') {
			// Unquoted — read to next whitespace, '/', or '>'
			int end = valStart;
			while (end < tag.length()) {
				char c = tag.charAt(end);
				if (c == ' ' || c == '/' || c == '>')
					break;
				end++;
			}
			return tag.substring(valStart, end);
		}
		int valEnd = tag.indexOf(quote, valStart + 1);
		if (valEnd < 0)
			throw new IllegalArgumentException("Unterminated attribute for '" + attr + "' in: " + tag);
		return tag.substring(valStart + 1, valEnd);
	}

	// =========================================================================
	// Internal mesh data holder
	// =========================================================================

	/** Flat, unindexed or indexed mesh arrays used only during file parsing. */
	private record RawMesh(float[] vertices, int[] triangles) {
	}

}