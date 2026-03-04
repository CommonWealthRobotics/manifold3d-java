
package manifold3d;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;


import manifold3d.UIntVector;
import manifold3d.FloatVector;
import manifold3d.IntVector;

import java.io.IOException;
import java.io.File;

import manifold3d.ManifoldPair;
import manifold3d.ManifoldVector;
import manifold3d.manifold.MeshGL;
import manifold3d.manifold.ExportOptions;

import manifold3d.pub.Box;
import manifold3d.pub.Polygons;
import manifold3d.pub.PolygonsVector;
import manifold3d.pub.SmoothnessVector;
import manifold3d.pub.OpType;

import manifold3d.linalg.DoubleVec3Vector;
import manifold3d.linalg.DoubleMat3x4;
import manifold3d.linalg.DoubleVec2;
import manifold3d.linalg.DoubleVec3;
import manifold3d.linalg.IntegerVec3;

import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.annotation.*;

public class NativeLoader{
	
	private static boolean loaded=false;	
		
	public static void loadAll() {
		if(loaded)
			return;
		loaded=true;			
	    String osName = System.getProperty("os.name").toLowerCase();
	    if (osName.contains("linux")) {
	        try {
	            System.load(Loader.extractResource("/libmanifold.so", null, "libmanifold", ".so").getAbsolutePath());
	        } catch (IOException e) {
	        	e.printStackTrace();
	            //throw new RuntimeException(e);
	        }
	    } else if (osName.contains("windows")) {
	        try {
	            System.out.println("Loading manifold");
	            System.load(Loader.extractResource("/manifold.dll", null, "manifold", ".dll").getAbsolutePath());
	            System.out.println("Finished Loading.");
	        } catch (IOException e) {
	        	e.printStackTrace();
	            //throw new RuntimeException(e);
	        }
	    } else if (osName.contains("mac")) {
	        try {
	            System.out.println("Loading Manifold");
	            //System.load(Loader.extractResource("/manifold3d/manifold/macosx-arm64/libmanifold.dylib", null, "libmanifold", ".dylib").getAbsolutePath());
	            System.load(Loader.extractResource("/libmanifold.3.4.0.dylib", null, "libmanifold", ".dylib").getAbsolutePath());                System.out.println("Finished Loading.");
	        } catch (IOException e) {
	        	e.printStackTrace();
	            //throw new RuntimeException(e);
	        }
	    } else {
	        throw new UnsupportedOperationException("Unsupported operating system: " + osName);
	    }
	    Loader.load();
	}
}
