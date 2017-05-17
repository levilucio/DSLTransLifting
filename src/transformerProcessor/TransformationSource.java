package transformerProcessor;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import dsltrans.FilePort;
import dsltrans.MetaModelIdentifier;

import transformerProcessor.exceptions.TransformationSourceException;
import emfInterpreter.EMFLoader;
import emfInterpreter.instance.InstanceAttribute;
import emfInterpreter.instance.InstanceEntity;
import emfInterpreter.instance.InstanceRelation;
import emfInterpreter.metamodel.MetaModelDatabase;

import lifting.ClauseVariableRatio;

public class TransformationSource extends TransformationUnit {
	private final FilePort _port;
	
	public TransformationSource(String classdir, FilePort p) {
		super(classdir);
		_port = p;
		setMetaDatabase(null);
		setDatabase(null);
		_presenceCondsDatabase = new Hashtable<String,String>();	
		_featureFormula = null;
	}

	public FilePort getPort() {
		return _port;
	}

	@Override
	public void Check() {
		setValid(false); // let's assume that it is not valid..
		if(getMetaDatabase() == null)
			return;
		if(getDatabase() == null)
			return;
		// TODO check if loaded model is compatible with loaded meta model
		
		setValid(true); // if passed all above conditions then it is valid
	}

	public void Load(Map<String, Object> factorys, Map<String, Object> metamodels) throws TransformationSourceException {
		if(isProcessed()) return;
		MetaModelIdentifier mmi = getPort().getMetaModelId();
		
		String mmName = mmi.getMetaModelName();
		String mmPath = mmi.getMetaModelURI();
		
		String inputPath = getPort().getFilePathURI();
		String presenceConditionPath = getClassdir() + "/" + inputPath.substring(0, inputPath.indexOf('.')) + "_presence_cond" + ".csv";
		
		EMFLoader loader = new EMFLoader();
		if(!metamodels.containsKey(mmName)) {
			loader.loadMetaModel(getClassdir(), mmPath);
			metamodels.put(mmName,loader.getMetaModelDatabase());
		} else {
			loader.setMetaModelDatabase((MetaModelDatabase) metamodels.get(mmName));
		}
		
		try {
			System.out.println("metamodel: "+mmName);
			loader.getDatabase().setFactorys(factorys);
			loader.loadDatabase(mmName, inputPath,getClassdir());
			loader.getDatabase().createTransitiveGraph();
			setDatabase(loader.getDatabase());
			setMetaDatabase(loader.getMetaModelDatabase());
			ArrayList<ArrayList<String>> inputPresenceConds = LoadPresenceConditions(presenceConditionPath);
			buildPresenceCondsDatabase(inputPresenceConds);
			
			ClauseVariableRatio cvr = new ClauseVariableRatio();
			// output the clause to variable ratio at the beginning of the transformation
			System.out.println();
			System.out.println("-----------------------------------------");
			System.out.println("CLAUSE_RATIO_START");
			System.out.println("CLAUSE_NUMBER: " + Integer.toString(cvr.calculateClauseToVarRatio(this.getPresenceConds(), this.getFeatureFormula())));
			System.out.println("NUMBER_CLASSES: " + Integer.toString(this.getDatabase().getLoadedClasses().size()));					
			System.out.println("NUMBER_RELATIONS: " + Integer.toString(this.getDatabase().getLoadedRelations().size()));
			System.out.println("-----------------------------------------");
			System.out.println();
			
			this.setProcessed(true);
		} catch (SecurityException e) {
			throw new TransformationSourceException("SecurityException at:", this, e);
		} catch (IllegalArgumentException e) {
			throw new TransformationSourceException("IllegalArgumentException at:", this, e);			
		} catch (ClassNotFoundException e) {
			throw new TransformationSourceException("ClassNotFoundException at:", this, e);
		} catch (NoSuchFieldException e) {
			throw new TransformationSourceException("NoSuchFieldException at:", this, e);
		} catch (IllegalAccessException e) {
			throw new TransformationSourceException("IllegalAccessException at:", this, e);
		} catch (NoSuchMethodException e) {
			throw new TransformationSourceException("NoSuchMethodException at:", this, e);
		} catch (InvocationTargetException e) {
			throw new TransformationSourceException("InvocationTargetException at:", this, e);
		} catch (FileNotFoundException e) {
			throw new TransformationSourceException("Could not find presence conditions file: " + presenceConditionPath, this, e);
		}
	}
	
	/*
	 * Load and return the presence condition file associated to this source file
	 * Needed by the lifted DSLTrans
	 * @param The file looked for has the same name as the input source file, but with the string "_presence_cond" before the extension
	 * @return An array of arrays containing the multiple entries in the csv file
	 */
	private ArrayList<ArrayList<String>> LoadPresenceConditions(String presCondFileName)
			throws FileNotFoundException {
		// initialize the presence condition array
		ArrayList<ArrayList<String>> presenceConds = new ArrayList<ArrayList<String>>();
		// read presence conditions into a variable sized array
		Scanner scanner;
		try {
			scanner = new Scanner(new File(presCondFileName));
	        // Set the delimiter used in file
	        scanner.useDelimiter(";");
	        ArrayList<String> csvLine = new ArrayList<String>();
	        String token = null;
	        // Get all tokens
	        while (scanner.hasNext())
	        {
	        	token = scanner.next();
	        	// remove newline from the string if it exists
	        	if (token.indexOf('\n') != -1) {
	        		String tokenBeforeNewline = token.substring(0, token.indexOf('\n'));
	        		String tokenAfterNewline = token.substring((token.indexOf('\n'))+1,token.length());
	        		csvLine.add(tokenBeforeNewline);
	        		presenceConds.add(csvLine);
	        		csvLine = new ArrayList<String>();
	        		csvLine.add(tokenAfterNewline);	        		
	        	}
	        	else
	        		csvLine.add(token);
	        	if (!scanner.hasNext())
	        		presenceConds.add(csvLine);
	        }	         
	        //close the scanner 
	        scanner.close();
		}
		catch (Exception e) {
			throw new FileNotFoundException(e.toString());
		}
		
		for (ArrayList<String> csvLine : presenceConds){
			for(String token : csvLine){
				System.out.print(token + " ");
			}
			System.out.print("\n");
		}
		
		return presenceConds;
	}

	/*
	 * Build the presence condition database by associating a presence condition to each 
	 * class and association in the EMF database, loaded from an external file
	 * Needed by the lifted DSLTrans
	 * @param 
	 * @return
	 */
	private void buildPresenceCondsDatabase(ArrayList<ArrayList<String>> inputPresenceConds) {
		// first place the feature formula in the feature formula attribute
		_featureFormula = (inputPresenceConds.get(0)).get(0);
		inputPresenceConds.remove(0);
		
		// now associate presence conditions with the right EMF objects in memory
		for(ArrayList<String> elementPresenceCond: inputPresenceConds) {
			// in case it's an object
			if (elementPresenceCond.size() == 3) {
				//System.out.println("It's an object!");
				for(InstanceEntity ie: getDatabase().getLoadedClasses()) {
					//System.out.println("----------------");
					//System.out.println("Type: " + ie.getDotNotation().replace("'",""));		
					//System.out.println("Type looked for: " + elementPresenceCond.get(0));
					//System.out.println("----------------");
					if (ie.getDotNotation().replace("'","").equals(elementPresenceCond.get(0))) {
						//System.out.println("Found correct loaded EMF object!");
						for(InstanceAttribute ia: getDatabase().getAttributesByInstanceEntity(ie)){
							//System.out.println("Attribute value: " + ia.getValue());
							//System.out.println("Looking for value: " + elementPresenceCond.get(1));
							if (ia.getMetaAttribute().getName().equals("Name") && 
									ia.getValue().equals(elementPresenceCond.get(1))) {
								//System.out.println("Adding element to the presence condition database");
								System.out.println("Added object " + ia.getValue() + " to the presence condition database");
								_presenceCondsDatabase.put(Integer.toString(ie.hashCode()), elementPresenceCond.get(2));
							}					
						}
					}
				}
			}
			// in case it's a relation
			if (elementPresenceCond.size() == 6) {
				//System.out.println("It's a relation!");
				for(InstanceRelation ir: getDatabase().getLoadedRelations()) {
					if (ir.getRelation().getName().equals(elementPresenceCond.get(0))) {
						//System.out.println("Found correct loaded EMF relation!");
						InstanceEntity iesource = ir.getSource();
						InstanceEntity ietarget = ir.getTarget();
						boolean foundsource = false;
						String source = null;
						String target = null;
						boolean foundtarget = false;
						if (iesource.getDotNotation().replace("'","").equals(elementPresenceCond.get(1)) &&
							ietarget.getDotNotation().replace("'","").equals(elementPresenceCond.get(3))) {
							for(InstanceAttribute ia: getDatabase().getAttributesByInstanceEntity(iesource)){
								//System.out.println("Attribute name: " + ia.getMetaAttribute().getName());
								//System.out.println("Attribute value: " + ia.getValue());
								//System.out.println("Looking for value: " + elementPresenceCond.get(1));
								if (ia.getMetaAttribute().getName().equals("Name") && 
										ia.getValue().toString().equals(elementPresenceCond.get(2))) {
									foundsource = true;
									source = ia.getValue().toString();
								}
							}
							for(InstanceAttribute ia: getDatabase().getAttributesByInstanceEntity(ietarget)){
								//System.out.println("Attribute name: " + ia.getMetaAttribute().getName());
								//System.out.println("Attribute value: " + ia.getValue());
								//System.out.println("Looking for value: " + elementPresenceCond.get(1));
								if (ia.getMetaAttribute().getName().equals("Name") && 
										ia.getValue().toString().equals(elementPresenceCond.get(4))) {
									foundtarget = true;
									target = ia.getValue().toString();
								}								
							}
							if (foundsource && foundtarget) {
								System.out.println("Added a relation " + source + "<--" +
									ir.getRelation().getName() + "-->" + target + " to the presence condition database");
								_presenceCondsDatabase.put(Integer.toString(ir.hashCode()), elementPresenceCond.get(5));
							}
						}
					}
				}
			}
		}
	}
	

	
	public String getMetamodelIdentifier()
	{ 
		MetaModelIdentifier mmi = getPort().getMetaModelId();
		return mmi.getMetaModelURI();
	}
	
}
