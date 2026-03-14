package com.cad;

import java.lang.foreign.MemorySegment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

public class ManifoldCADApp extends Application {

	private ManifoldBindings manifold;
	private ManifoldMesh converter;
	private MeshView currentMeshView;
	private Group meshGroup;
	private Label shape1Label;
	private Label shape2Label;
	private Label infoLabel;
	private boolean wireframeMode = false;
	private Stage stage;
	private java.io.File lastStlPath;
	private String stlFilename = "STL";
	private MemorySegment stlTemplate;
	private MemorySegment booleanResult;
	private Rotate yawRotate;
	private Rotate pitchRotate;
	private double stlOriginalWidth, stlOriginalHeight, stlOriginalDepth;

	// Store last 2 shapes for boolean operations
	private List<MemorySegment> shapeHistory = new ArrayList<>();
	private List<String> shapeNames = new ArrayList<>();
	private List<Integer> shapeVertCounts = new ArrayList<>();
	private List<String> shapeParams = new ArrayList<>();

	private static final int MAX_HISTORY = 2;
	private boolean differenceReversed = false;

	// Parameter controls
	private TextField scaleXField, scaleYField, scaleZField, segmentsField;

	private void loadNativeLibrary(String libName) {
		try {
			// Detect platform
			String os = System.getProperty("os.name").toLowerCase();
			String arch = System.getProperty("os.arch").toLowerCase();
			
			if (arch.equals("amd64"))
				arch = "x86_64";
			
			String platform;
			String extension;
			if (os.contains("win")) {
				platform = "win-" + arch;
				extension = ".dll";
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
			java.io.File libsDir = new java.io.File("libs");
			if (!libsDir.exists())
				libsDir.mkdirs();
			
			java.io.File libFile = new java.io.File(libsDir, fullName);
			java.io.File devFile = new java.io.File("src/main/resources/natives/" + platform + "/" + fullName);

			System.out.println("Searching library: " +  devFile.getAbsolutePath());
			
			// Check timestamp, copy only newer
			boolean needsCopy = !libFile.exists();
			if (!needsCopy && devFile.exists())
				needsCopy = devFile.lastModified() > libFile.lastModified();
			
			if (needsCopy) {
				if (devFile.exists()) {
					java.nio.file.Files.copy(devFile.toPath(), libFile.toPath(), 
						java.nio.file.StandardCopyOption.REPLACE_EXISTING,
						java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
						libFile.setExecutable(true);
				} else {
					try (java.io.InputStream in = getClass().getResourceAsStream("/natives/" + platform + "/" + fullName)) {
						if (in == null)
							throw new RuntimeException("Library not found: " + fullName + " for platform " + platform);

						java.nio.file.Files.copy(in, libFile.toPath(), 
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
						System.out.println("Extracted to libs/: " + fullName);
					}
				}
			}
			
			System.load(libFile.getAbsolutePath());
			
		} catch (Exception e) {
			throw new RuntimeException("Failed to load: " + libName, e);
		}
	}

	@Override
	public void start(Stage stage) {

		this.stage = stage;
		try {
			loadNativeLibrary("libmanifold");
			loadNativeLibrary("libmanifoldc");

			manifold = new ManifoldBindings();
			converter = new ManifoldMesh(manifold);
		} catch (Exception  e) {
			e.printStackTrace();
			Platform.exit();
			return;
		}

		stage.setTitle("Manifold Java Demo");
		stage.setWidth(1024);
		stage.setHeight(800);
		// Create nested rotation groups to prevent gimbal lock
		// yawGroup rotates around world Y (left/right mouse)
		Group yawGroup = new Group();
		Rotate yawRotate = new Rotate(0, Rotate.Y_AXIS);
		yawGroup.getTransforms().add(yawRotate);

		// pitchGroup rotates around local X (up/down mouse) - contains your 90 degree initial angle
		Group pitchGroup = new Group();
		Rotate pitchRotate = new Rotate(90, Rotate.X_AXIS); // Your "right view" angle
		pitchGroup.getTransforms().add(pitchRotate);

		// meshGroup holds the actual 3D objects
		meshGroup = new Group();
		pitchGroup.getChildren().add(meshGroup);
		yawGroup.getChildren().add(pitchGroup);

		Group sceneRoot = new Group(yawGroup);

		AmbientLight ambient = new AmbientLight(Color.color(0.6, 0.6, 0.6));
		PointLight pointLight = new PointLight(Color.color(0.5, 0.5, 0.5));
		pointLight.setTranslateX(1000);
		pointLight.setTranslateY(-1000);
		pointLight.setTranslateZ(-10000);
		pointLight.setConstantAttenuation(1);
		pointLight.setLinearAttenuation(0);
		pointLight.setQuadraticAttenuation(0);

		sceneRoot.getChildren().addAll(ambient, pointLight);

		SubScene subScene = new SubScene(sceneRoot, 1000, 620, true, SceneAntialiasing.BALANCED);
		subScene.setFill(Color.WHITE);

		PerspectiveCamera camera = new PerspectiveCamera(true);
		camera.setTranslateZ(-500);
		camera.setNearClip(0.1);
		camera.setFarClip(10000.0);
		subScene.setCamera(camera);

		// Store last mouse position for delta calculation
		final double[] lastX = {0};
		final double[] lastY = {0};

		subScene.setOnMousePressed(e -> {
			lastX[0] = e.getSceneX();
			lastY[0] = e.getSceneY();
		});

		subScene.setOnMouseDragged(e -> {
			double deltaX = e.getSceneX() - lastX[0];
			double deltaY = e.getSceneY() - lastY[0];

			// Left/Right: Rotate yawGroup around World Y (turntable)
			yawRotate.setAngle(yawRotate.getAngle() - deltaX * 0.3);
			
			// Up/Down: Rotate pitchGroup around its local X (pitch)
			pitchRotate.setAngle(pitchRotate.getAngle() + deltaY * 0.3);

			lastX[0] = e.getSceneX();
			lastY[0] = e.getSceneY();
		});

		subScene.setOnScroll(e -> {
			double delta = e.getDeltaY();
			camera.setTranslateZ(camera.getTranslateZ() + delta);
		});

		infoLabel = new Label("Ready");
		infoLabel.setTextFill(Color.BLACK);

		shape1Label = new Label("Shape 1: [none]");
		shape1Label.setTextFill(Color.DARKBLUE);
		shape2Label = new Label("Shape 2: [none]");
		shape2Label.setTextFill(Color.DARKBLUE);

		// Update button
		Button updateBtn = new Button("Update");
		updateBtn.setOnAction(e -> updateLastShape());

		// Reset button
		Button resetBtn = new Button("Reset to 20.0");
		resetBtn.setOnAction(e -> {
			scaleXField.setText("20.0");
			scaleYField.setText("20.0");
			scaleZField.setText("20.0");
		});

		// Refine button
		Button refineBtn = new Button("Refine(2)");
		refineBtn.setOnAction(e -> doRefine());

		Button info1Btn = new Button("Info 1");
		info1Btn.setOnAction(e -> doInfo1());

		Button stlBtn = new Button("STL");
		stlBtn.setDisable(true); // Disable until STL is loaded
		stlBtn.setOnAction(e -> {
			if (stlTemplate == null) {
				infoLabel.setText("Error: Import an STL file first");
				return;
			}
			try {
				MemorySegment mesh = manifold.asOriginal(stlTemplate);
				BoundingBox bounds = manifold.getJavaFXBounds(mesh);
				String params = String.format("%s (%.2f x %.2f x %.2f)", 
					stlFilename, bounds.getWidth(), bounds.getHeight(), bounds.getDepth());
				addToHistory("STL", mesh, params);
				showGeometry(mesh, false);
			} catch (Throwable t) {
				error(t);
			}
		});

		// Import button
		Button importBtn = new Button("Import STL");
		importBtn.setOnAction(e -> {
			javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
			fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("STL Files", "*.stl"));
			
			java.io.File initialDir = new java.io.File(System.getProperty("user.dir"));
			if ((lastStlPath != null) && lastStlPath.exists())
				initialDir = lastStlPath.getParentFile();

			fc.setInitialDirectory(initialDir);
			
			java.io.File file = fc.showOpenDialog(stage);
			if (file != null) {
				// Delete old template if exists
				if (stlTemplate != null) {
					try {
						manifold.delete(stlTemplate);
					}
					catch (Throwable t) {}
					stlTemplate = null;
				}
				
				lastStlPath = file;
				stlFilename = file.getName();
				stlTemplate = importStlFile(file.toPath());
				if (stlTemplate != null) {
					BoundingBox bounds = manifold.getJavaFXBounds(stlTemplate);
					stlOriginalWidth  = bounds.getWidth();
					stlOriginalHeight = bounds.getHeight();
					stlOriginalDepth  = bounds.getDepth();
					
					scaleXField.setText(String.format("%.2f", stlOriginalWidth));
					scaleYField.setText(String.format("%.2f", stlOriginalHeight));
					scaleZField.setText(String.format("%.2f", stlOriginalDepth));
					
					try { // ADD A COPY to history, keep original as template
						MemorySegment displayCopy = manifold.asOriginal(stlTemplate);
						String params = String.format("STL %s (%.2f x %.2f x %.2f)", 
							stlFilename, stlOriginalWidth, stlOriginalHeight, stlOriginalDepth);
						addToHistory("STL", displayCopy, params);
						showGeometry(displayCopy, false);
						stlBtn.setDisable(false); // Enable the STL button
					} catch (Throwable t) {
						error(t);
					}
				}
			}
		});

		// Export Button
		Button exportBtn = new Button("Export STL");
		exportBtn.setOnAction(e -> exportCurrentStl());

		// Parameter controls
		scaleXField = new TextField("20.0"); scaleXField.setPrefWidth(60);
		scaleYField = new TextField("20.0"); scaleYField.setPrefWidth(60);
		scaleZField = new TextField("20.0"); scaleZField.setPrefWidth(60);
		segmentsField = new TextField("128"); segmentsField.setPrefWidth(60);

		// Enter key triggers update
		scaleXField.setOnAction(e -> updateLastShape());
		scaleYField.setOnAction(e -> updateLastShape());
		scaleZField.setOnAction(e -> updateLastShape());
		segmentsField.setOnAction(e -> updateLastShape());

		HBox paramBox = new HBox(5, new Label("X/H:"), scaleXField,
								  new Label("Y/R1:"), scaleYField,
								  new Label("Z/R2:"), scaleZField,
								  new Label("Seg:"), segmentsField,
								  updateBtn, resetBtn, refineBtn, info1Btn, importBtn, exportBtn);

		paramBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		paramBox.setPadding(new Insets(5));

		Button tetraBtn = new Button("Tetra");
		tetraBtn.setOnAction(e -> {
			MemorySegment s = createTetrahedron();
			if (s != null) {
				addToHistory("Tetra", s, String.format("X=%.2f Y=%.2f Z=%.2f", 
					getParam(scaleXField, 1.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0)));
				showGeometry(s, false);
			}
		});

		Button cubeBtn = new Button("Cube");
		cubeBtn.setOnAction(e -> {
			MemorySegment s = createCube();
			if (s != null) {
				addToHistory("Cube", s, String.format("X=%.2f Y=%.2f Z=%.2f", 
					getParam(scaleXField, 1.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0)));
				showGeometry(s, false);
			}
		});

		Button sphereBtn = new Button("Sphere");
		sphereBtn.setOnAction(e -> {
			MemorySegment s = createSphere();
			if (s != null) {
				addToHistory("Sphere", s, String.format("X=%.2f Y=%.2f Z=%.2f", 
					getParam(scaleXField, 1.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0)));
				showGeometry(s, false);
			}
		});

		Button cylBtn = new Button("Cylinder");
		cylBtn.setOnAction(e -> {
			MemorySegment s = createCylinder();
			if (s != null) {
				addToHistory("Cylinder", s, String.format("H=%.2f R1=%.2f R2=%.2f", 
					getParam(scaleXField, 3.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0)));
				showGeometry(s, false);
			}
		});

		Button unionBtn = new Button("Union");
		unionBtn.setOnAction(e -> doBooleanOperation("Union", (a, b) -> {
			try {
				return manifold.union(a, b);
			}
			catch (Throwable t) {
				return null;
			}
		}));

		Button batchUnionBtn = new Button("Batch Union");
		batchUnionBtn.setOnAction(e -> doBatchUnion());

		Button diffBtn = new Button("Difference");
		diffBtn.setOnAction(e -> doDifferenceToggle());

		Button intersectBtn = new Button("Intersection");
		intersectBtn.setOnAction(e -> doBooleanOperation("Intersection", (a, b) -> {
			try {
				return manifold.intersection(a, b);
			}
			catch (Throwable t) {
				return null;
			}
		}));

		Button clearBtn = new Button("Clear");
		clearBtn.setOnAction(e -> clearAll());

		Button hullBtn = new Button("Hull");
		hullBtn.setOnAction(e -> doHull());

		Button batchHullBtn = new Button("Batch Hull");
		batchHullBtn.setOnAction(e -> doBatchHull());

		Button xorBtn = new Button("XOR");
		xorBtn.setOnAction(e -> doXor());

		Button wireBtn = new Button("Wireframe");
		wireBtn.setOnAction(e -> {
			wireframeMode = !wireframeMode;
			wireBtn.setText(wireframeMode ? "Solid" : "Wireframe");
			if (currentMeshView != null)
				currentMeshView.setDrawMode(wireframeMode ? DrawMode.LINE : DrawMode.FILL);
		});

		HBox toolbar = new HBox(10, tetraBtn, cubeBtn, sphereBtn, cylBtn, 
					   stlBtn, unionBtn, batchUnionBtn, diffBtn, intersectBtn, xorBtn,
					   hullBtn, batchHullBtn, clearBtn, wireBtn);

		toolbar.setPadding(new Insets(10));
		toolbar.setStyle("-fx-background-color: #dddddd;");

		VBox topBox = new VBox(paramBox, toolbar, shape1Label, shape2Label, infoLabel);
		topBox.setPadding(new Insets(5));
		topBox.setStyle("-fx-background-color: #f0f0f0;");

		BorderPane root = new BorderPane();
		root.setCenter(subScene);
		root.setTop(topBox);

		Scene scene = new Scene(root, 800, 700);
		stage.setScene(scene);
		stage.show();

		// Add starting cube
		MemorySegment initialCube = createCube();
		if (initialCube != null) {
			addToHistory("Cube", initialCube, String.format("X=%.2f Y=%.2f Z=%.2f", 
				getParam(scaleXField, 1.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0)));

			showGeometry(initialCube, false);
		}

		infoLabel.setText("Create shapes first, then use boolean operations");

	} // END OF START

	private void doRefine() {
		if (shapeHistory.isEmpty()) {
			infoLabel.setText("Error: No shape to refine");
			return;
		}

		int lastIdx = shapeHistory.size() - 1;
		int vertCount = shapeVertCounts.get(lastIdx);

		if (vertCount > 10000) {
			infoLabel.setText("REFINE BLOCKED: " + vertCount + " vertices (>10K)");
			return;
		}

		MemorySegment result = null;
		boolean addedToHistory = false;
		
		try {
			long startTime = System.nanoTime();
			String lastName = shapeNames.get(lastIdx);
			String lastParams = shapeParams.get(lastIdx);

			MemorySegment last = shapeHistory.get(lastIdx);
			result = manifold.refine(last, 2);
			
			// Validate immediately
			try { manifold.numVert(result); }
			catch (Throwable ignored) {}
			
			long duration = (System.nanoTime() - startTime) / 1_000_000;

			if (result != null) {
				// Delete old shape and remove from lists
				MemorySegment old = shapeHistory.remove(lastIdx);
				manifold.safeDelete(old);
				shapeNames.remove(lastIdx);
				shapeVertCounts.remove(lastIdx);
				shapeParams.remove(lastIdx);

				// Add refined shape with updated name
				addToHistory(lastName, result, lastParams);
				addedToHistory = true;
				
				showGeometry(result, false);
				infoLabel.setText("Refine(2) completed, " + duration + "ms");
			}
		} catch (Throwable t) {
			error(t);
			// Only cleanup if not yet managed by history
			if (!addedToHistory) {
				manifold.safeDelete(result);
			}
		}
	}

	private void doInfo1() {

		MemorySegment target = booleanResult;
		if ((target == null) && !shapeHistory.isEmpty())
			target = shapeHistory.get(shapeHistory.size() - 1);

		if (target == null) {
			infoLabel.setText("Error: No object to analyze");
			return;
		}
		
		try {
			long nVert = manifold.numVert(target);
			long nTri = manifold.numTri(target);
			long nEdge = manifold.numEdge(target);
			long nProp = manifold.numProp(target);
			double volume = manifold.volume(target);
			double area = manifold.surfaceArea(target);
			double epsilon = manifold.epsilon(target);
			int genus = manifold.genus(target);
			long manSize = manifold.manifoldSize();
			long objectMemSize = manifold.estimateMemoryBytes(target);
			String unit = "Bytes";
			if (objectMemSize > 1024 * 1024) {
				objectMemSize >>= 20;
				unit = "MB";
			}
			else if (objectMemSize > 1024) {
				objectMemSize >>= 10;
				unit = "KB";
			}

			infoLabel.setText(String.format(
				"Verts=%d  Tri=%d  Edges=%d  Props=%d  Volume=%.2f  Surface=%.2f  Holes=%d  Epsilon=%.12f  Mem Size=%d%s",
				nVert, nTri, nEdge, nProp, volume, area, genus, epsilon, objectMemSize, unit
			));
		} catch (Throwable t) {
			error(t);
		}
	}

	private void doXor() {
		if (shapeHistory.size() < 2) {
			infoLabel.setText("Error: Need 2 shapes for XOR");
			return;
		}
		
		MemorySegment aMinusB = null;
		MemorySegment bMinusA = null;
		MemorySegment result = null;
		
		try {
			long startTime = System.nanoTime();
			MemorySegment a = shapeHistory.get(0);
			MemorySegment b = shapeHistory.get(1);

			aMinusB = manifold.difference(a, b);
			bMinusA = manifold.difference(b, a);
			result = manifold.union(aMinusB, bMinusA);
			
			// Validate immediately to catch errors early
			manifold.numVert(result);
			
			long duration = (System.nanoTime() - startTime) / 1_000_000;

			if (result != null) {
				// Clean up previous result BEFORE assigning new one
				if (booleanResult != null) {
					try { manifold.delete(booleanResult); } catch (Throwable t) {}
				}
				booleanResult = result;
				showGeometry(booleanResult, false);
				infoLabel.setText("XOR completed, " + duration + "ms");
			}
		} catch (Throwable t) {
			error(t);
			// Clean up failed result
			if (result != null)
				try {
					manifold.delete(result);
				}
				catch (Throwable ignored) {}
		} finally {
			manifold.safeDelete(aMinusB);
			manifold.safeDelete(bMinusA);
		}
	}

	private void doDifferenceToggle() {
		if (shapeHistory.size() < 2) {
			infoLabel.setText("Error: Need 2 shapes for Difference");
			return;
		}

		MemorySegment a = shapeHistory.get(0);
		MemorySegment b = shapeHistory.get(1);
		MemorySegment result = null;

		try {
			String mode = differenceReversed ? "B-A" : "A-B";
			result = differenceReversed ? manifold.difference(b, a) : manifold.difference(a, b);
			
			long startTime = System.nanoTime();
			try { manifold.numVert(result); }
			catch (Throwable ignored) {}
			long duration = (System.nanoTime() - startTime) / 1_000_000;

			if (result != null) {
				// Delete old result safely before assigning new
				manifold.safeDelete(booleanResult);
				booleanResult = result;
				showGeometry(booleanResult, false);
				differenceReversed = !differenceReversed;
				infoLabel.setText("Difference " + mode + ", " + duration + "ms");
			}
		} catch (Throwable t) {
			// Clean up failed result
			manifold.safeDelete(result);
			error(t);
		}
	}

	private void updateLastShape() {
		if (shapeHistory.isEmpty()) {
			infoLabel.setText("No shape to update");
			return;
		}

		int lastIdx = shapeHistory.size() - 1;
		String lastName = shapeNames.get(lastIdx);
		MemorySegment newShape = null;
		String params = "";

		switch(lastName) {
			case "Tetra":
				newShape = createTetrahedron();
				params = String.format("X=%.2f Y=%.2f Z=%.2f", 
					getParam(scaleXField, 1.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0));
				break;
			case "Cube":
				newShape = createCube();
				params = String.format("X=%.2f Y=%.2f Z=%.2f", 
					getParam(scaleXField, 1.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0));
				break;
			case "Sphere":
				newShape = createSphere();
				params = String.format("X=%.2f Y=%.2f Z=%.2f", 
					getParam(scaleXField, 1.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0));
				break;
			case "Cylinder":
				newShape = createCylinder();
				params = String.format("H=%.2f R1=%.2f R2=%.2f", 
					getParam(scaleXField, 3.0), getParam(scaleYField, 1.0), getParam(scaleZField, 1.0));
				break;
			case "STL":
				// Get desired dimensions from fields
				double targetW = getParam(scaleXField, stlOriginalWidth);
				double targetH = getParam(scaleYField, stlOriginalHeight);
				double targetD = getParam(scaleZField, stlOriginalDepth);
				
				// Calculate scale factors (target / original)
				double sx = targetW / stlOriginalWidth;
				double sy = targetH / stlOriginalHeight;
				double sz = targetD / stlOriginalDepth;
				
				MemorySegment base = null;
				try {
					base = manifold.asOriginal(stlTemplate);
					newShape = manifold.scale(base, sx, sy, sz);
					manifold.safeDelete(base); // Clean up unscaled copy
					base = null; // Mark as deleted
					params = String.format("STL %.2f x %.2f x %.2f", targetW, targetH, targetD);
				} catch (Throwable t) {
					manifold.safeDelete(base); // Clean up on failure too
					error(t);
					return;
				}
				break;
			default:
				infoLabel.setText("Cannot update: " + lastName);
				return;
		}

		if (newShape != null) {
			manifold.safeDelete(shapeHistory.remove(lastIdx));
			shapeNames.remove(lastIdx);
			shapeVertCounts.remove(lastIdx);
			shapeParams.remove(lastIdx);

			addToHistory(lastName, newShape, params);
			showGeometry(newShape, false);
			infoLabel.setText("Updated: " + lastName);
		}
	}

	private double getParam(TextField field, double defaultVal) {
		try {
			return Double.parseDouble(field.getText());
		} catch (NumberFormatException e) {
			return defaultVal;
		}
	}

	private int getSegments() {
		try {
			return Math.max(3, Integer.parseInt(segmentsField.getText()));
		} catch (NumberFormatException e) {
			return 32;
		}
	}

	private void addToHistory(String name, MemorySegment shape, String params) {

		if (shape == null)
			return;
		
		// Clear boolean result when adding new shapes
		if (booleanResult != null) {
			try {
				manifold.safeDelete(booleanResult);
			} catch (Throwable t) {}
			booleanResult = null;
		}

		int verts = 0;
		try {
			verts = (int) manifold.exportMeshGL(shape).vertCount();
		} catch (Throwable t) {}

		if (shapeHistory.size() >= MAX_HISTORY) {
			MemorySegment old = shapeHistory.remove(0);
			manifold.safeDelete(old); // Safe even if already freed
			shapeNames.remove(0);
			shapeVertCounts.remove(0);
			shapeParams.remove(0);
		}

		shapeHistory.add(shape);
		shapeNames.add(name);
		shapeVertCounts.add(verts);
		shapeParams.add(params);
		updateShapesLabel();
	}

	private void updateShapesLabel() {
		if (shapeNames.isEmpty()) {
			shape1Label.setText("Shape 1: [none]");
			shape2Label.setText("Shape 2: [none]");
		} else if (shapeNames.size() == 1) {
			shape1Label.setText(String.format("Shape 1: %s (%s) Vertices=%d - Click 1 more shape...", 
				shapeNames.get(0), shapeParams.get(0), shapeVertCounts.get(0)));
			shape2Label.setText("Shape 2: [Click a shape you want to add...]");
		} else {
			shape1Label.setText(String.format("Shape 1: %s (%s) Vertices=%d", 
				shapeNames.get(0), shapeParams.get(0), shapeVertCounts.get(0)));
			shape2Label.setText(String.format("Shape 2: %s (%s) Vertices=%d",
				shapeNames.get(1), shapeParams.get(1), shapeVertCounts.get(1)));
		}
	}

	private void doBooleanOperation(String opName, BooleanOp op) {
		if (shapeHistory.size() < 2) {
			infoLabel.setText("Error: 2 shapes are required for " + opName);
			return;
		}

		MemorySegment a = shapeHistory.get(0);
		MemorySegment b = shapeHistory.get(1);

		long startTime = System.nanoTime();
		MemorySegment result = op.apply(a, b);
		try { manifold.numVert(result); }
		catch (Throwable ignored) {}
		long duration = (System.nanoTime() - startTime) / 1_000_000;

		if (result != null) {
			// Store as temporary result, don't add to history
			booleanResult = result;
			showGeometry(booleanResult, false);
			infoLabel.setText(opName + " completed, " + duration + "ms (Export to save)");
		} else
			infoLabel.setText("Error: " + opName + " failed");

	}

	private void doBatchUnion() {
		if (shapeHistory.size() < 2) {
			infoLabel.setText("Error: Need 2 shapes in history for Batch Union");
			return;
		}
		
		try {
			// Create tetrahedron for this test
			MemorySegment tetra = createTetrahedron();
			if (tetra == null) {
				infoLabel.setText("Error: Failed to create tetrahedron");
				return;
			}
			
			// Prepare array: tetrahedron + 2 history shapes = 3 total
			MemorySegment[] shapes = new MemorySegment[3];
			shapes[0] = tetra;
			shapes[1] = shapeHistory.get(0);
			shapes[2] = shapeHistory.get(1);
			
			long startTime = System.nanoTime();
			MemorySegment result = manifold.batchUnion(shapes);
			
			// Validate result (forces manifold evaluation)
			try { manifold.numVert(result); }
			catch (Throwable ignored) {}
			
			long duration = (System.nanoTime() - startTime) / 1_000_000;
			
			// Cleanup temporary tetrahedron (history shapes remain managed)
			try { manifold.delete(tetra); } catch (Throwable t) {}
			
			if (result != null) {
				// Store as temporary result (like other boolean ops)
				if (booleanResult != null) {
					try { manifold.delete(booleanResult); } catch (Throwable t) {}
				}
				booleanResult = result;
				showGeometry(booleanResult, false);
				infoLabel.setText("Batch Union completed (tetra + 2 shapes), " + duration + "ms");
			} else {
				infoLabel.setText("Error: Batch Union failed");
			}
		} catch (Throwable t) {
			error(t);
		}
	}

	private void doHull() {
		MemorySegment source = booleanResult; // Use temporary result if available
		
		if ((source == null) && !shapeHistory.isEmpty())
			source = shapeHistory.get(shapeHistory.size() - 1); // Else use last shape

		if (source == null) {
			infoLabel.setText("Error: No shape for hull");
			return;
		}
		
		try {
			long startTime = System.nanoTime();
			MemorySegment result = manifold.hull(source);
			try { manifold.numVert(result); }
			catch (Throwable ignored) {}
			long duration = (System.nanoTime() - startTime) / 1_000_000;

			if (result != null) {
				// Clean up previous temp result
				if (booleanResult != null) {
					try { manifold.delete(booleanResult); }
					catch (Throwable t) {}
				}
				booleanResult = result; // Store as temporary exportable result
				
				showGeometry(booleanResult, false);
				infoLabel.setText("Hull completed, " + duration + "ms  Vertices=" + manifold.numVert(result));
			}
		} catch (Throwable t) {
			error(t);
		}
	}

	private void doBatchHull() {
		if (shapeHistory.size() < 2) {
			infoLabel.setText("Error: Need 2 shapes in history for Batch Hull");
			return;
		}
		
		try {
			// Create tetrahedron for this test
			MemorySegment tetra = createTetrahedron();
			if (tetra == null) {
				infoLabel.setText("Error: Failed to create tetrahedron");
				return;
			}
			
			// Prepare array: tetrahedron + 2 history shapes = 3 total
			MemorySegment[] shapes = new MemorySegment[3];
			shapes[0] = tetra;
			shapes[1] = shapeHistory.get(0);
			shapes[2] = shapeHistory.get(1);
			
			long startTime = System.nanoTime();
			MemorySegment result = manifold.batchHull(shapes);
			
			// Validate result (forces manifold evaluation)
			try { manifold.numVert(result); }
			catch (Throwable ignored) {}
			
			long duration = (System.nanoTime() - startTime) / 1_000_000;
			
			// Cleanup temporary tetrahedron (history shapes remain managed)
			try { manifold.delete(tetra); } catch (Throwable t) {}
			
			if (result != null) {
				// Store as temporary result
				if (booleanResult != null) {
					try { manifold.delete(booleanResult); } catch (Throwable t) {}
				}
				booleanResult = result;
				showGeometry(booleanResult, false);
				infoLabel.setText("Batch Hull completed (tetra + 2 shapes), " + duration + "ms");
			} else {
				infoLabel.setText("Error: Batch Hull failed");
			}
		} catch (Throwable t) {
			error(t);
		}
	}


	private void clearHistory() {
		// Delete native objects BEFORE clearing list to free memory immediately
		for (MemorySegment seg : shapeHistory)
			manifold.safeDelete(seg);

		shapeHistory.clear();
		shapeNames.clear();
		shapeVertCounts.clear();
		shapeParams.clear();
		
		// Clear temporary boolean result
		manifold.safeDelete(booleanResult);
		booleanResult = null;
		
		updateShapesLabel();
		System.gc(); // Hint JVM after massive native cleanup
	}

	private void clearAll() {

		//manifold.safeDelete(stlTemplate);
		//stlTemplate = null;
		//stlBtn.setDisable(true);
		
		clearHistory();
		
		if (currentMeshView != null) {
			meshGroup.getChildren().remove(currentMeshView);
			currentMeshView.setMesh(null);
			currentMeshView = null;
		}

		System.gc();
	}

	private MemorySegment createTetrahedron() {
		try {
			double sx = getParam(scaleXField, 1.0);
			double sy = getParam(scaleYField, 1.0);
			double sz = getParam(scaleZField, 1.0);
			MemorySegment tetra = manifold.tetrahedron();
			MemorySegment result = manifold.scale(tetra, sx, sy, sz);
			
			manifold.safeDelete(tetra);
			return result;

		} catch (Throwable t) {
			error(t);
			return null;
		}
	}

	private MemorySegment createCube() {
		try {
			double sx = getParam(scaleXField, 1.0);
			double sy = getParam(scaleYField, 1.0);
			double sz = getParam(scaleZField, 1.0);

			return manifold.cube(sx, sy, sz, true);

		} catch (Throwable t) {
			error(t);

			return null;
		}
	}

	private MemorySegment createSphere() {
		try {
			double sx = getParam(scaleXField, 1.0);
			double sy = getParam(scaleYField, 1.0);
			double sz = getParam(scaleZField, 1.0);
			int seg = getSegments();

			MemorySegment sphere = manifold.sphere(1.0, seg);
			MemorySegment result = manifold.scale(sphere, sx, sy, sz);
			
			manifold.safeDelete(sphere);
			return result;

		} catch (Throwable t) {
			error(t);
			return null;
		}
	}

	private MemorySegment createCylinder() {
		try {
			double height = getParam(scaleXField, 3.0);
			double rBottom = getParam(scaleYField, 1.0);
			double rTop = getParam(scaleZField, 1.0);
			int seg = getSegments();

			return manifold.cylinder(height, rBottom, rTop, seg, 1);
		} catch (Throwable t) {
			error(t);
			return null;
		}
	}

	private void showGeometry(MemorySegment manifoldPtr, boolean shouldDelete) {
		if (manifoldPtr == null) return;
		
		try {
			// Remove old mesh from scene first to allow GC
			if (currentMeshView != null) {
				meshGroup.getChildren().remove(currentMeshView);
				currentMeshView.setMesh(null); // Release mesh reference
				currentMeshView = null;
			}
			
			// Force GC of old mesh before allocating new one
			System.gc();
			
			TriangleMesh mesh = converter.toJavaFXMesh(manifoldPtr);
			currentMeshView = new MeshView(mesh);
			currentMeshView.setDrawMode(wireframeMode ? DrawMode.LINE : DrawMode.FILL);
			currentMeshView.setCullFace(CullFace.NONE);

			PhongMaterial material = new PhongMaterial();
			Color transRed = new Color(1, 0, 0, 1.0);
			material.setDiffuseColor(transRed);
			material.setSpecularColor(transRed);
			material.setSpecularPower(1.0);
			currentMeshView.setMaterial(material);

			meshGroup.getChildren().add(currentMeshView);

		} catch (Throwable t) {
			error(t);
		} finally {
			if (shouldDelete)
				manifold.safeDelete(manifoldPtr);
		}
	}

	private MemorySegment importStlFile(Path filePath) {
		try (java.io.DataInputStream dis = new java.io.DataInputStream(
				new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(filePath)))) {

			dis.skipBytes(80); // 80 byte header

			// Get triangle count
			int triCount = Integer.reverseBytes(dis.readInt()); // STL is little-endian

			// Each triangle: 12 bytes normal + 36 bytes vertices + 2 bytes attribute = 50 bytes
			// We only care about vertices (3 vertices × 3 floats per triangle)
			float[] vertices = new float[triCount * 9]; // 3 verts × 3 coords per triangle
			int[] triangles = new int[triCount * 3];    // 3 indices per triangle

			for (int i = 0; i < triCount; i++) { // Skip normal (12 bytes)
				dis.skipBytes(12);

				// Read 3 vertices (9 floats)
				for (int j = 0; j < 9; j++) { // Little-endian float
					int bits = Integer.reverseBytes(dis.readInt());
					vertices[i * 9 + j] = Float.intBitsToFloat(bits);
				}

				dis.skipBytes(2);// Skip attribute (2 bytes)

				// Triangle indices (sequential)
				triangles[i * 3 + 0] = i * 3 + 0;
				triangles[i * 3 + 1] = i * 3 + 1;
				triangles[i * 3 + 2] = i * 3 + 2;
			}

			MemorySegment imported;
			MemorySegment centered;
			try {
				imported = manifold.importMeshGL(vertices, triangles, (long)triCount * 3, (long)triCount);
				centered = manifold.centerObject(imported);
				manifold.safeDelete(imported); // DELETE INTERMEDIATE
			} catch (Throwable t) {
				error(t);
				return null;
			}

			return centered;

		} catch (Exception e) {
			error(e);
			return null;
		}
	}

	private void exportCurrentStl() {
		MemorySegment toExport = booleanResult;
		
		// If no boolean result, export the most recent shape in history
		if ((toExport == null) && !shapeHistory.isEmpty())
			toExport = shapeHistory.get(shapeHistory.size() - 1);

		if (toExport == null) {
			infoLabel.setText("No object to export");
			return;
		}
		
		javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
		fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("STL Files", "*.stl"));
		
		// Remember last directory
		if ((lastStlPath != null) && lastStlPath.exists()) {
			java.io.File parentDir = lastStlPath.getParentFile();

			if (parentDir != null && parentDir.exists())
				fc.setInitialDirectory(parentDir);
		}
		
		java.io.File file = fc.showSaveDialog(stage);
		
		if (file == null)
			return;

		try {
			writeStlBinary(toExport, file.toPath());
			long triCount = manifold.numTri(toExport);
			infoLabel.setText("Exported: " + file.getName() + " (" + triCount + " triangles)");
		} catch (Throwable e) {
			error(e);
		}
	}

	private void writeStlBinary(MemorySegment manifoldPtr, java.nio.file.Path path) throws Throwable {
		ManifoldBindings.MeshData mesh = manifold.exportMeshGL(manifoldPtr);
		float[] verts = mesh.vertices();
		int[] tris = mesh.triangles();
		int triCount = mesh.triCount();

		try (java.io.DataOutputStream dos = new java.io.DataOutputStream(
				new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(path)))) {

			// 80 byte header
			byte[] header = new byte[80];
			System.arraycopy("Manifold CAD Export".getBytes(), 0, header, 0, 19);
			dos.write(header);

			// Triangle count (little endian)
			dos.writeInt(Integer.reverseBytes(triCount));

			for (int i = 0; i < triCount; i++) {
				int idx0 = tris[i * 3] * 3;
				int idx1 = tris[i * 3 + 1] * 3;
				int idx2 = tris[i * 3 + 2] * 3;

				float x0 = verts[idx0], y0 = verts[idx0 + 1], z0 = verts[idx0 + 2];
				float x1 = verts[idx1], y1 = verts[idx1 + 1], z1 = verts[idx1 + 2];
				float x2 = verts[idx2], y2 = verts[idx2 + 1], z2 = verts[idx2 + 2];

				// Calculate normal
				float nx = (y1 - y0) * (z2 - z0) - (z1 - z0) * (y2 - y0);
				float ny = (z1 - z0) * (x2 - x0) - (x1 - x0) * (z2 - z0);
				float nz = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (len > 0) {
					nx /= len; ny /= len; nz /= len;
				}

				// Write normal + 3 vertices (little endian floats)
				writeFloatLE(dos, nx); writeFloatLE(dos, ny); writeFloatLE(dos, nz);
				writeFloatLE(dos, x0); writeFloatLE(dos, y0); writeFloatLE(dos, z0);
				writeFloatLE(dos, x1); writeFloatLE(dos, y1); writeFloatLE(dos, z1);
				writeFloatLE(dos, x2); writeFloatLE(dos, y2); writeFloatLE(dos, z2);

				dos.writeShort(0); // Attribute byte count
			}
		}
	}

	private void writeStlBinary22222(TriangleMesh mesh, java.nio.file.Path path) throws java.io.IOException {
		float[] points = new float[mesh.getPoints().size()];
		mesh.getPoints().copyTo(0, points, 0, points.length);

		int[] faces = new int[mesh.getFaces().size()];
		mesh.getFaces().copyTo(0, faces, 0, faces.length);

		int triCount = faces.length / 6;

		try (java.io.DataOutputStream dos = new java.io.DataOutputStream(
				new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(path)))) {

			// Write 80 byte header
			byte[] header = new byte[80];
			System.arraycopy("Manifold CAD Export".getBytes(), 0, header, 0, 19);
			dos.write(header);

			// Write triangle count (little endian)
			dos.writeInt(Integer.reverseBytes(triCount));

			for (int i = 0; i < triCount; i++) {
				int p1 = faces[i * 6 + 0] * 3;
				int p2 = faces[i * 6 + 2] * 3;
				int p3 = faces[i * 6 + 4] * 3;

				float x1 = points[p1 + 0];
				float y1 = points[p1 + 1];
				float z1 = points[p1 + 2];
				float x2 = points[p2 + 0];
				float y2 = points[p2 + 1];
				float z2 = points[p2 + 2];
				float x3 = points[p3 + 0];
				float y3 = points[p3 + 1];
				float z3 = points[p3 + 2];

				// Calculate normal
				float nx = (y2 - y1) * (z3 - z1) - (z2 - z1) * (y3 - y1);
				float ny = (z2 - z1) * (x3 - x1) - (x2 - x1) * (z3 - z1);
				float nz = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (len > 0) {
					nx /= len;
					ny /= len;
					nz /= len;
				}

				// Write normal and vertices as little-endian floats
				writeFloatLE(dos, nx); writeFloatLE(dos, ny); writeFloatLE(dos, nz);
				writeFloatLE(dos, x1); writeFloatLE(dos, y1); writeFloatLE(dos, z1);
				writeFloatLE(dos, x2); writeFloatLE(dos, y2); writeFloatLE(dos, z2);
				writeFloatLE(dos, x3); writeFloatLE(dos, y3); writeFloatLE(dos, z3);

				// Attribute (2 bytes)
				dos.writeShort(0);
			}
		}
	}

	private void writeFloatLE(java.io.DataOutputStream dos, float f) throws java.io.IOException {
		int bits = Float.floatToIntBits(f);
		// Write little-endian: least significant byte first
		dos.writeByte((bits		 ) & 0xFF);
		dos.writeByte((bits >>  8) & 0xFF);
		dos.writeByte((bits >> 16) & 0xFF);
		dos.writeByte((bits >> 24) & 0xFF);
	}

	private void error(Throwable t) {
		infoLabel.setText("Error: " + t.getMessage());
		t.printStackTrace();
	}

	@Override
	public void stop() {
		clearAll(); // Now uses safeDelete for everything
		if (manifold != null)
			manifold.close();
	}

	@FunctionalInterface
	interface BooleanOp {
		MemorySegment apply(MemorySegment a, MemorySegment b);
	}

	public static void main(String[] args) {
		launch(args);
	}

}