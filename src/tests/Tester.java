package tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import solver.Z3Solver;
import solver.Z3Model.Z3Bool;
import transformerProcessor.TransformerProcessor;
import transformerProcessor.exceptions.InvalidLayerRequirement;
import transformerProcessor.exceptions.TransformationLayerException;
import transformerProcessor.exceptions.TransformationSourceException;


//import java.io.File;
//import java.io.FileFilter;
//import java.io.FilenameFilter;
//import java.util.LinkedList;
//import java.util.List;
//
//import transformerProcessor.TransformerProcessor;
//import transformerProcessor.exceptions.InvalidLayerRequirement;
//import transformerProcessor.exceptions.TransformationLayerException;
//import transformerProcessor.exceptions.TransformationSourceException;
//import difflib.Delta;
//import difflib.DiffUtils;
//import difflib.Patch;
//
public class Tester {
	
	public void genericTest(String projectDir, String transFile, String[] features) {
		String expected = projectDir + "expected_output_pc.csv";
		String actual = projectDir + "output_presence_cond.csv";
		
		// run the transformation
		TransformerProcessor tP = new TransformerProcessor(projectDir);
		tP.LoadModel(transFile);
		
		try {
			tP.Execute();
		} catch (InvalidLayerRequirement e) {
			System.err.println("Error running transformation: " + transFile);
			e.printStackTrace();
		} catch (TransformationSourceException e) {
			System.err.println("Error running transformation: " + transFile);
			e.printStackTrace();
		} catch (TransformationLayerException e) {
			System.err.println("Error running transformation: " + transFile);
			e.printStackTrace();
		}
		
		// base part of any Z3 query for a particular example
		String query = "";
		
		for(String feature : features)
			query += "(declare-const " + feature + " Bool) ";
		
		BufferedReader ar = null, er = null;
		
		try {
			ar = new BufferedReader(new FileReader(actual));
			er = new BufferedReader(new FileReader(expected));
			
			Hashtable<String, String> actualContents = new Hashtable<String, String>();
			Hashtable<String, String> expectedContents = new Hashtable<String, String>();
			
			String line, key, value;
			
			// fill hashtables with the actual content and expected content
			while((line = ar.readLine()) != null) {
				key = line.contains(",") ? line.substring(0, line.lastIndexOf(',')) : null;
				value = line.substring(line.lastIndexOf(',') + 1);
				// key might be null (e.g. with the first line, the feature model)
				actualContents.put(((key == null) ? value : key), value); 
			}
			
			while((line = er.readLine()) != null) {
				key = line.contains(",") ? line.substring(0, line.lastIndexOf(',')) : null;
				value = line.substring(line.lastIndexOf(',') + 1);
				// key might be null (e.g. with the first line, the feature model)
				expectedContents.put(((key == null) ? value : key), value); 
			}
			
			// same of number elements in output implies that hashtables should be the same size
			assertEquals(actualContents.size(), expectedContents.size());
			
			Enumeration<String> actualKeys = actualContents.keys();
			
			// while the presence conditions can differ in appearance (despite being equivalent)
			// for two corresponding elements, the name must be exactly the same
			// then every key (and no other) in the actualContents table should also be a key of the expectedContents table
			
			// order of elements in expected and actual output does not matter 
			while(actualKeys.hasMoreElements()) {
				String actKey = actualKeys.nextElement();
				
				// test equivalence using Z3
				Z3Solver mySolver = new Z3Solver();
				Z3Bool result1 = mySolver.checkSat(query + "(assert (and (not " + actualContents.get(actKey) + ") " + expectedContents.get(actKey) + "))");
				Z3Bool result2 = mySolver.checkSat(query + "(assert (and (not " + expectedContents.get(actKey) + ") " + actualContents.get(actKey) + "))");
			
				assertEquals(result1, Z3Bool.UNSAT);
				assertEquals(result2, Z3Bool.UNSAT);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				ar.close();
				er.close();
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
	}
//
//	public static final String TESTS_DIR = "tests";
//	public static final String TEMPS_DIR = "tempClasses";
//	public static final String TEST_FOLDER_PREFIX = "t";
//	public static final String OUTPUT_MODEL_PREFIX = "o";
//	public static final String CORRECT_MODEL_PREFIX = "c";
//	public static final String MODEL_EXT = "xmi";
//	public static final String TRANSFORMATION_EXT = "dsltrans";
//	private static final String PROJECT_DIR = ".";
//	private static final Object CVS_DIR_NAME = "CVS";
//	
//	
//	private static final boolean BULK_TESTING = false;
//	private static final String TEST = "t0";
//	private static final boolean COMPARE_RESULTS = false;
//	
//	public static void main(String[] args) {
//		
//		File testsDir = new File(TESTS_DIR);
//		
////		validTestsDir(testsDir);
//		
//		runTests(testsDir);
//	}
//
//	private static void runTests(File testsDir) {
//		File[] testSubDirectories = getIndividualTestDirs(testsDir);
//		
//		assert testSubDirectories!=null;
//		
//		for (File testDir : testSubDirectories) {
//			try {
//				runIndividualTest(testDir);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//	}
//
//	private static void runIndividualTest(File testDir) throws IOException {
//		if (shouldIgnoreDir(testDir)) {
//			return;
//		}
//		
//		
//		File transformation = getTransformationFile(testDir);
//		createOrCleanTempDir();
//		runTransformation(transformation);
//		createOrCleanTempDir();
//		
//		if (COMPARE_RESULTS) {
//			checkCorrectness(testDir);
//		}
//	}
//
//	private static void checkCorrectness(File testDir) throws IOException {
//		File[] outputFiles = getOutputModels(testDir);
//		
//		if (outputFiles == null || outputFiles.length==0) {
//			throw new RuntimeException("No output files found: "
//					+ testDir.getCanonicalPath());
//		}
//		
//		for(File outputFile : outputFiles) {
//			File correspondingCorrectFile = getCorrectModel(testDir, outputFile);
//			
//			List<String> outputFileLines = getFileLines(outputFile);
//			List<String> correctFileLines = getFileLines(correspondingCorrectFile);
//			
//			Patch patch = DiffUtils.diff(outputFileLines, correctFileLines);
//			
//			List<Delta> deltas = patch.getDeltas();
//			
//			for (Delta delta: deltas) {
//				System.err.println("##### " + testDir.getAbsolutePath() + ": " + outputFile.getName() + " <-->  " + correspondingCorrectFile.getName() + " #####");
//	            System.err.println(delta);
//	            System.err.println("#########################");
//	        }
//		}
//	}
//	
//	private static List<String> getFileLines(File file) {
//        List<String> lines = new LinkedList<String>();
//        String line = "";
//        try {
//                BufferedReader in = new BufferedReader(new FileReader(file));
//                while ((line = in.readLine()) != null) {
//                        lines.add(line);
//                }
//        } catch (IOException e) {
//                e.printStackTrace();
//        }
//        return lines;
//}
//
//	private static void runTransformation(File transformation) {
//		TransformerProcessor tP = new TransformerProcessor(PROJECT_DIR);
//		tP.LoadModel(transformation.getAbsolutePath());
//		
//		try {
//			tP.Execute();
//		} catch (InvalidLayerRequirement e) {
//			System.err.println("Error running transformation: " + transformation.getAbsolutePath());
//			e.printStackTrace();
//		} catch (TransformationSourceException e) {
//			System.err.println("Error running transformation: " + transformation.getAbsolutePath());
//			e.printStackTrace();
//		} catch (TransformationLayerException e) {
//			System.err.println("Error running transformation: " + transformation.getAbsolutePath());
//			e.printStackTrace();
//		}
//	}
//
//	private static File getTransformationFile(File testDir) {
//		File[] files = getTransformationFiles(testDir);
//		assert files.length == 1;
//		return files[0];
//	}
//
//	private static void validTestsDir(File testsDir) {
//		try {
//			if (!testsDir.exists()) {
//				throw new RuntimeException("Dir does not exist: "
//						+ testsDir.getCanonicalPath());
//			}
//			if (!testsDir.canRead()) {
//				throw new RuntimeException("Can't read in dir "
//						+ testsDir.getCanonicalPath());
//			}
//			if (!testsDir.canWrite()) {
//				throw new RuntimeException("Can't write in dir "
//						+ testsDir.getCanonicalPath());
//			}
//			if (!testsDir.isDirectory()) {
//				throw new RuntimeException("This must be a directory: "
//						+ testsDir.getCanonicalPath());
//			}
//			
//			validTestsDirFiles(testsDir);
//			
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}
//
//	private static void validTestsDirFiles(File testsDir) throws IOException {
//		File[] inidivualTestDirs = getIndividualTestDirs(testsDir);
//		
//		assert inidivualTestDirs!=null;
//		
//		for (File testDir : inidivualTestDirs) {
//			validTestDirFiles(testDir);
//		}
//	}
//
//	private static File[] getIndividualTestDirs(File testsDir) {
//		assert testsDir.isDirectory();
//		
//		FileFilter fileFilter = new FileFilter() {
//		    public boolean accept(File file) {
//		        return file.isDirectory() && hasValidName(file.getName());
//		    }
//
//			private boolean hasValidName(String name) {
//				return (BULK_TESTING)?true:name.equals(TEST);
//			}
//		};
//		
//		return testsDir.listFiles(fileFilter);
//	}
//
//	private static void validTestDirFiles(File testDir) throws IOException {
//		if (shouldIgnoreDir(testDir)) {
//			return;
//		}
//		if (getTransformationFiles(testDir).length != 1) {
//			throw new RuntimeException("Only one transformation is permitted: "
//					+ testDir.getCanonicalPath());
//		}
//		
//		if (getCorrectModels(testDir).length == 0) {
//			System.err.println("Warning: No correct output models found: "
//					+ testDir.getCanonicalPath());
//		}
//		
//		createOrCleanTempDir();
//	}
//
//	private static boolean shouldIgnoreDir(File testDir) {
//		if (testDir.getName().equals(CVS_DIR_NAME)) {
//			return true;
//		}
//		// Add more ignore condicions here.
//		
//		
//		return false;
//	}
//
//	private static File[] getOutputModels(File testDir) {
//		assert testDir.isDirectory();
//		
//		FileFilter correctModels = new FileFilter() {
//			@Override
//			public boolean accept(File file) {
//				
//				if (file.isDirectory()) {
//					return false;
//				}
//				
//				String name = file.getName();
//				
//				String prefix = getPrefix(name);
//				
//				return prefix.equals(OUTPUT_MODEL_PREFIX);
//			}
//		};
//		
//		return testDir.listFiles(correctModels);
//	}
//
//	private static File getCorrectModel(File testDir, final File outputFile) throws IOException {
//		assert testDir.isDirectory();
//		
//		FileFilter correctModelsCorresponding = new FileFilter() {
//			@Override
//			public boolean accept(File file) {
//				
//				if (file.isDirectory()) {
//					return false;
//				}
//				
//				String prefix = getPrefix(file.getName());
//				String name = getNameWithoutPrefix(file.getName());
//				
//				if (!prefix.equals(CORRECT_MODEL_PREFIX)) {
//					return false;
//				}
//				
//				return name.equals(getNameWithoutPrefix(outputFile.getName()));
//			}
//
//		};
//		
//		File[] result = testDir.listFiles(correctModelsCorresponding);
//		
//		if (result.length != 1) {
//			throw new RuntimeException("Too many or none correct output models found: "
//					+ testDir.getCanonicalPath());
//		}
//		
//		return result[0];
//	}
//	
//	private static String getNameWithoutPrefix(String name) {
//		return name.substring(1);
//	}
//	
//	private static String getPrefix(String name) {
//		return name.substring(0,1);
//	}
//
//	private static File[] getCorrectModels(final File testDir) {
//		assert testDir.isDirectory();
//		
//		FileFilter correctModels = new FileFilter() {
//			@Override
//			public boolean accept(File file) {
//				
//				if (file.isDirectory()) {
//					return false;
//				}
//				
//				String name = file.getName();
//				
//				String initChar = name.substring(0,1);
//				
//				return initChar.equals(CORRECT_MODEL_PREFIX);
//			}
//		};
//		
//		return testDir.listFiles(correctModels);
//	}
//
//	private static File[] getTransformationFiles(final File testDir) {
//		
//		assert testDir.isDirectory();
//		
//		FilenameFilter transformationsFilter = new FilenameFilter() {
//			@Override
//			public boolean accept(File dir, String name) {
//				assert testDir.equals(dir);
//				
//				String extension="";
//				
//				int mid= name.lastIndexOf(".");
//				if (mid==-1) {
//					return false;
//				}
//				
//				extension=name.substring(mid+1,name.length());
//				
//				return extension.equals(TRANSFORMATION_EXT);
//			}
//		};
//		
//		return testDir.listFiles(transformationsFilter);
//	}
//
//	private static void createOrCleanTempDir() throws IOException {
//		File tempDir = getTempDir();
//		
//		if (!tempDir.exists()) {
//			if (!tempDir.mkdir()) {
//				throw new RuntimeException("Could not create temp dir "
//						+ tempDir.getCanonicalPath());
//			}
//		}
//		
//		if (!tempDir.isDirectory()) {
//			throw new RuntimeException("This is not a dir: "
//					+ tempDir.getCanonicalPath());
//		}
//		
//		assert tempDir.exists() && tempDir.isDirectory();
//		
//		cleanTempDir(tempDir);
//	}
//
//	private static File getTempDir() throws IOException {
//		return new File(PROJECT_DIR + "/" + TEMPS_DIR );
//	}
//
//	private static void cleanTempDir(File tempDir) throws IOException {
//		File[] files = tempDir.listFiles();
//		if (files==null) {
//			return;
//		}
//		
//		for (File file : files) {
//			deleteRecursively(file);
//		}
//	}
//	
//	private static void deleteRecursively(File f) throws IOException {
//		  if (f.isDirectory()) {
//		    for (File c : f.listFiles())
//		    	deleteRecursively(c);
//		  }
//		  if (!f.delete())
//		    System.err.println("Failed to delete file: " + f.getCanonicalPath());
//		}
//
}
