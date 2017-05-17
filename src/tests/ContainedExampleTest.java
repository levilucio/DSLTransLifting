package tests;

import static org.junit.Assert.*;

import org.junit.Test;

public class ContainedExampleTest {
	final String MAIN_DIR = "/home/kawin/workspace/DSLTrans_lifting/lifted_tests/contained_example/";
	String[] features = { "_H1", "_D1", "_D2", "_F1", "_F2", "_H1D1", "_H1D2", "_D1F1", "_D2F2" };
	
	// test positive indirect links with in-between classes and associations
	@Test
	public void positiveIndirectTrans() {
		String projectDir = MAIN_DIR + "positive_indirect/"; 
		String transFile = projectDir + "positive_indirect_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test negative indirect links with in-between classes and associations 
	@Test
	public void negativeIndirectTrans() {
		String projectDir = MAIN_DIR + "negative_indirect/"; 
		String transFile = projectDir + "negative_indirect_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test multiple negative indirect links with the same source class 
	@Test
	public void doubleNegativeTrans() {
		String projectDir = MAIN_DIR + "double_negative/"; 
		String transFile = projectDir + "double_negative_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
}
