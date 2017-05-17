package tests;

import static org.junit.Assert.*;

import org.junit.Test;

public class ComplexExampleTest {
	final String MAIN_DIR = "/home/kawin/workspace/DSLTrans_lifting/lifted_tests/complex_example/";
	String[] features = { "_H1", "_H2", "_D1", "_D2", "_D3", "_F", "_H1D1", "_H2D1", "_H2D2", "_H2D3", "_D1F", "_D2F" };
	
	// test lifted existential matching where one universal match site only has 1 existential match site
	// the other universal match site has 2 existential match sites 
	@Test
	public void smallTrans() {
		String projectDir = MAIN_DIR + "small/"; 
		String transFile = projectDir + "small_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted existential matching where existential component is larger (multiple objects)
	// one universal match site only has 1 existential match site
	// the other universal match site has 2 existential match sites 
	@Test
	public void largeTrans() {
		String projectDir = MAIN_DIR + "large/"; 
		String transFile = projectDir + "large_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted existential matching with only existential component
	// one of the produced objects should have PC 'false' (i.e. it can never exist)
	@Test
	public void allExistTrans() {
		String projectDir = MAIN_DIR + "all_exist/"; 
		String transFile = projectDir + "all_exist_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
	// test lifted NACs where one output exists normally (without lifting)
	// and second output is a result of lifting
	@Test
	public void NACFullTrans() {
		String projectDir = MAIN_DIR + "NAC_full/"; 
		String transFile = projectDir + "NAC_full_trans.dsltrans";
		(new Tester()).genericTest(projectDir, transFile, features);
	}
	
}
