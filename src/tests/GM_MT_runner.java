package tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Iterator;

import solver.Z3Model.Z3Bool;
import solver.Z3Solver;
import transformerProcessor.TransformerProcessor;
import transformerProcessor.exceptions.InvalidLayerRequirement;
import transformerProcessor.exceptions.TransformationLayerException;
import transformerProcessor.exceptions.TransformationSourceException;

public class GM_MT_runner {

	public static void main(String[] args) {

		//System.out.println("Result: " + mySolver.checkSat("p"));
		final String PROJECT_DIR, TRANS;
		
		if(args.length == 2) {
			PROJECT_DIR = args[0];
			TRANS = args[1];
		} else {
			PROJECT_DIR = "/home/kawin/workspace/DSLTrans_lifting/lifted_tests/contained_example/double_negative/";
			TRANS = "/home/kawin/workspace/DSLTrans_lifting/lifted_tests/contained_example/double_negative/double_negative_trans.dsltrans";
//			PROJECT_DIR = "/home/kawin/workspace/DSLTrans_lifting/GM_MT/";
//			TRANS = "/home/kawin/workspace/DSLTrans_lifting/GM_MT/GM_MT.dsltrans";
			
		}
	
		TransformerProcessor tP = new TransformerProcessor(PROJECT_DIR);
		tP.LoadModel(TRANS);
		
		try {
			tP.Execute();
		} catch (InvalidLayerRequirement e) {
			System.err.println("Error running transformation: " + TRANS);
			e.printStackTrace();
		} catch (TransformationSourceException e) {
			System.err.println("Error running transformation: " + TRANS);
			e.printStackTrace();
		} catch (TransformationLayerException e) {
			System.err.println("Error running transformation: " + TRANS);
			e.printStackTrace();
		}
	}
}	


