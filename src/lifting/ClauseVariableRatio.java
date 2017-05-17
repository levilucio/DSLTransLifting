package lifting;

import java.util.Enumeration;
import java.util.Hashtable;

import java.util.List;
import java.util.ArrayList;

public class ClauseVariableRatio {
	
	public int calculateClauseToVarRatio(Hashtable<String,String> presenceConds, String featureFormula) {
		Enumeration<String> e = presenceConds.keys();
		int numberOfClausesInConds = 0;
		int numberOfObjects = presenceConds.size();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			numberOfClausesInConds += this.countCharsInString(presenceConds.get(key), '(');
		}
		// this counts on the fact that every variable names starts by 'F' and has no more Fs in its name
		int numberOfVarsInFeatureFormula = this.countCharsInString(featureFormula, 'F');
		
		//return ((float)numberOfClausesInConds / (float)numberOfObjects) / (float)numberOfVarsInFeatureFormula;
//		ArrayList returnValue = new ArrayList<Integer>();
//		returnValue.add(numberOfClausesInConds);
//		returnValue.add(numberOfVarsInFeatureFormula);
		return numberOfClausesInConds;
		
	}

	private int countCharsInString(String s, char c) {
		int counter = 0;
		if (s != null) {
			for( int i=0; i<s.length(); i++ ) {
			    if( s.charAt(i) == c ) {
			        counter++;
			    } 
			}
		}
		return counter;
	}
}
