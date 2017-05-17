package tests;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import solver.Z3Model.Z3Bool;
import solver.Z3Solver;

public class SimpleExampleTest {
	final String MAIN_DIR = "/home/kawin/workspace/DSLTrans_lifting/lifted_tests/simple_example/";
	String[] features = { "_H1", "_H2", "_D1", "_D2", "_H1D1", "_H1D2", "_H2D1", "_H2D2" };
	
	// test lifted existential matching with universal and existential components
	@Test
	public void smallTrans() {
		String projectDir = MAIN_DIR + "small/"; 
		String transFile = projectDir + "small_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted existential matching with larger universal component (multiple objects)
	@Test
	public void largeTrans() {
		String projectDir = MAIN_DIR + "large/"; 
		String transFile = projectDir + "large_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted negative application condition with only classes, no associations
	@Test
	public void NACClassTrans() {
		String projectDir = MAIN_DIR + "NAC_class/"; 
		String transFile = projectDir + "NAC_class_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted negative application condition with only associations, no classes as negative matchers
	@Test
	public void NACAssocTrans() {
		String projectDir = MAIN_DIR + "NAC_assoc/"; 
		String transFile = projectDir + "NAC_assoc_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted negative application condition with both classes and associations
	@Test
	public void NACFullTrans() {
		String projectDir = MAIN_DIR + "NAC_full/"; 
		String transFile = projectDir + "NAC_full_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted negative application condition with existential matching
	@Test
	public void NACExistTrans() {
		String projectDir = MAIN_DIR + "NAC_exist/"; 
		String transFile = projectDir + "NAC_exist_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted negative application condition when match part of the rule is the same as NAC
	@Test
	public void NACSelfTrans() {
		String projectDir = MAIN_DIR + "NAC_self/"; 
		String transFile = projectDir + "NAC_self_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	

}
