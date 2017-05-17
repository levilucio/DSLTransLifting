package post_processor;

import org.eclipse.core.resources.IFile;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.emf.common.util.URI;

public class PostProcessorUtils {

	public static void refreshOutputFile(URI fileURI) throws CoreException {
				
		System.out.println("Refreshing output...");
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		
		System.out.println("Workspace obtained...");
		IWorkspaceRoot root = workspace.getRoot();
		
		IPath outputPath = Path.fromOSString(fileURI.path());
		System.out.println("OutputPath obtained...");
		IFile outFile = root.getFile(outputPath);
	
		
		System.out.println("OutFile obtained...");
		outFile.refreshLocal(IResource.DEPTH_ZERO, null);
		
		System.out.println("Refresh done.");
		
		 
	}
	
	
	
}
