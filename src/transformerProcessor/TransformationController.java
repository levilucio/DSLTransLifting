package transformerProcessor;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Hashtable;
import java.io.FileWriter;
import java.io.IOException; 

import org.omg.CORBA._PolicyStub;

import transformerProcessor.exceptions.InvalidLayerRequirement;
import transformerProcessor.exceptions.TransformationLayerException;
import transformerProcessor.exceptions.TransformationSourceException;
import dsltrans.AbstractSource;
import dsltrans.FilePort;
import dsltrans.Layer;
import emfInterpreter.instance.InstanceAttribute;
import emfInterpreter.instance.InstanceDatabase;
import emfInterpreter.instance.InstanceEntity;
import emfInterpreter.instance.InstanceRelation;
import emfInterpreter.metamodel.MetaModelDatabase;
import lifting.ClauseVariableRatio;
import lifting.PresCondNode;

public class TransformationController {
	private final List<TransformationLayer> _units;
	private final List<TransformationSource> _sources;
	private final Map<String, Object> _factorys;
	private final Map<String, Object> _metamodels;
	private final String _classdir;
	
	TransformationController(String classdir) {
		_units = new LinkedList<TransformationLayer>();
		_sources = new LinkedList<TransformationSource>();
		_factorys = new HashMap<String, Object>();
		_metamodels = new HashMap<String, Object>();		
		_classdir = classdir;
	}
	
	public void add(Layer l) {
		getUnits().add(new TransformationSequentialLayer(getClassdir(), this, l));
		System.out.println("Getting layer...");
	}

	public void add(FilePort source) {
		System.out.println("Fileport: " + source.getName());
		getSources().add(new TransformationSource(getClassdir(),source));
	}

	/*
	 * Executes a DSLTrans transformation layer-per-layer
	 */
	public void Execute() throws InvalidLayerRequirement, TransformationSourceException, TransformationLayerException {
		while(canProcess()) {
			for(TransformationLayer l:getUnits()) {
				if(!l.isProcessed() && l.isValid()) {
					boolean toExecute = true;
					List<TransformationUnit> unitlist = new LinkedList<TransformationUnit>();
					for(AbstractSource as: l.getRequirements()) {
						TransformationUnit unit;
						unit = resolve(as);
						
//						System.out.println("Database" + getSources().get(0).getDatabase());
//						getSources().get(0).getDatabase().dump();
						
						if(!unit.isProcessed()) {
							toExecute = false;
							break;
						}
						if (l.getLayer().getPreviousSource().contains(as))
							unitlist.add(unit);
					}
					
					if(toExecute) {
						for(TransformationUnit unit:unitlist) {
							l.Execute( unit );
						}
						l.setRules(null);
						//releaseDatabases(getUnits());		
					}
				}

				Hashtable<String,String> presenceConds = l.getPresenceConds();
				Enumeration<String> e = presenceConds.keys();
				
				Hashtable<String,String> presenceCondsInOutput = new Hashtable<String,String>();
				ArrayList<String> csvOutput = new ArrayList<String>(); 
				
//				System.out.println("--------------------------------------------------------");	
//				System.out.println("Current database state: "); 

				for (InstanceEntity ie: l.getDatabase().getLoadedClasses()) {
					//System.out.println(Integer.toString(ie.hashCode()) + " : " + id);
					String name = null;
					if(!canProcess()) {
						for(InstanceAttribute ia: l.getDatabase().getAttributesByInstanceEntity(ie)) {
							if (ia.getMetaAttribute().getName().equals("shortName")) {
								name = ie.getMetaEntity().getName() + " " + ia.getValue().toString();
								csvOutput.add(name.replaceAll("\\s+", ",") + "," + presenceConds.get(Integer.toString(ie.hashCode()))); 
							}
						}
					}
					
					presenceCondsInOutput.put(Integer.toString(ie.hashCode()), presenceConds.get(Integer.toString(ie.hashCode())));
				}
				for (InstanceRelation ir: l.getDatabase().getLoadedRelations()) {
					//System.out.println(Integer.toString(ir.hashCode()) + " : " + id);
					String full_name = null, src_name = null, target_name = null; 
					InstanceEntity src = ir.getSource(), target = ir.getTarget(); 
					
					if(!canProcess()) {
						for(InstanceAttribute ia: l.getDatabase().getAttributesByInstanceEntity(src)) {
							if (ia.getMetaAttribute().getName().equals("shortName"))
								src_name = src.getMetaEntity().getName() + " " + ia.getValue().toString();
						}
					
						for(InstanceAttribute ia: l.getDatabase().getAttributesByInstanceEntity(target)) {
							if (ia.getMetaAttribute().getName().equals("shortName"))
								target_name = target.getMetaEntity().getName() + " " + ia.getValue().toString();
						}
						full_name = ir.getRelation().getName() + " " + src_name + " " + target_name;
						csvOutput.add(full_name.replaceAll("\\s+", ",") + "," + presenceConds.get(Integer.toString(ir.hashCode())));
					}

					presenceCondsInOutput.put(Integer.toString(ir.hashCode()), presenceConds.get(Integer.toString(ir.hashCode())));
				}			
				System.out.println("--------------------------------------------------------");
				
//				System.out.println("--------------------------------------------------------");	
//				System.out.println("Current match model: "); 
//
//				for (InstanceEntity ie: l.getMatchModel().getLoadedClasses()) {
//					//System.out.println(Integer.toString(ie.hashCode()) + " : " + id);
//					String name = null;
//					for(InstanceAttribute ia: l.getMatchModel().getAttributesByInstanceEntity(ie))
//						if (ia.getMetaAttribute().getName().equals("shortName"))
//							name = ia.getValue().toString();
//						System.out.println(name + ":" + presenceConds.get(Integer.toString(ie.hashCode())));
//				}
//				for (InstanceRelation ir: l.getMatchModel().getLoadedRelations()) {
//					//System.out.println(Integer.toString(ir.hashCode()) + " : " + id);		
//					System.out.println(ir.getRelation().getName() + ":" + presenceConds.get(Integer.toString(ir.hashCode())));
//				}			
//				System.out.println("--------------------------------------------------------");
				
//				System.out.println("-----------------> ");
//				System.out.println(l._inputPresenceCondsDatabase.keys());
//				System.out.println("-----------------> ");				
//				
				// build a copy of the presence condition database to remove the presence conditions for input objects
//				Hashtable<String,String> presenceCondsInOutput = new Hashtable<String,String>();
//				Enumeration<String> e_inout = l.getPresenceConds().keys();
//				while (e_inout.hasMoreElements()) {
//					boolean isOutputElem = false;
//					String id_inout = e_inout.nextElement();
//					for (InstanceEntity ie: l.getDatabase().getLoadedClasses()) { 
//						if (id_inout == Integer.toString(ie.hashCode())) {isOutputElem = true; System.out.println("-------------------> YES!!!");}
//					}
//					for (InstanceRelation ir: l.getDatabase().getLoadedRelations()) {
//						if (id_inout == Integer.toString(ir.hashCode()))  {isOutputElem = true; System.out.println("-------------------> YES!!!");}
//					}
//					
//					if (isOutputElem) presenceCondsInOutput.put(id_inout, presenceConds.get(id_inout));
//				}


				ClauseVariableRatio cvr = new ClauseVariableRatio();
				// output the clause to variable ratio at the beginning of the transformation
				System.out.println();
				System.out.println("-----------------------------------------");
				System.out.println("CLAUSE_RATIO_AFTER_LAYER: " + l.getName());
				System.out.println("CLAUSE_NUMBER: " + Integer.toString(cvr.calculateClauseToVarRatio(presenceCondsInOutput, l.getFeatureFormula())));
				System.out.println("NUMBER_PRESENCE_CONDS: " + Integer.toString(presenceCondsInOutput.size()));					
				System.out.println("NUMBER_CLASSES: " + Integer.toString(l.getDatabase().getLoadedClasses().size()));					
				System.out.println("NUMBER_RELATIONS: " + Integer.toString(l.getDatabase().getLoadedRelations().size()));					
				System.out.println("-----------------------------------------");
				System.out.println();
				
				if(!canProcess()) {
					String xmiFileName = l.getOutputFilePathURI();
					String csvFileName = xmiFileName.substring(0, xmiFileName.indexOf('.')) + "_presence_cond.csv"; 
					
					try {
						FileWriter writer = new FileWriter(_classdir + "/" + csvFileName); 
						writer.append(l.getFeatureFormula());
						writer.append('\n'); 
						
						for(String line : csvOutput) {
							writer.append(line); 
							writer.append('\n'); 
						}
						
						writer.flush();
						writer.close(); 
					} catch (IOException ie) {
						ie.printStackTrace();
					}
				}
			}
		}
	}
	
	/*
	private void releaseDatabases(List<TransformationLayer> list) {
		for(TransformationLayer transformationLayer:list) {
			if(transformationLayer.isProcessed() && transformationLayer.getGroupName().isEmpty()
				&& !belongsToSomeRequirements(transformationLayer)) {
				
				transformationLayer.setMetaDatabase(null);
				transformationLayer.setDatabase(null);
			}
		}
	}
	*/
	/*
	private boolean belongsToSomeRequirements(
			TransformationLayer tl) {

		for(TransformationLayer transformationLayer:getUnits()) {
			if(tl != transformationLayer && 
				!transformationLayer.isProcessed()) {
		
				for(AbstractSource as: transformationLayer.getRequirements()) {
					for(TransformationLayer l:getUnits()) {
						if(l.getLayer() == as && l == tl) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	*/
	private TransformationUnit resolve(AbstractSource requirement) throws InvalidLayerRequirement, TransformationSourceException {
		for(TransformationLayer l:getUnits()) {
			if(l.getLayer() == requirement && l.isValid())
				return l;
		}
		for(TransformationSource s:getSources()) {
			if(s.getPort() == requirement) {
				s.Load(_factorys,getMetamodels());
				s.Check();
				return s;
			}
		}
		throw new InvalidLayerRequirement("Cannot resolve source: " + requirement.toString());
	}

	private boolean canProcess() {
		for(TransformationLayer l:getUnits()) {
			if(!l.isProcessed() && l.isValid())
				return true;
		}
		return false;
	}

	public List<TransformationLayer> getUnits() {
		return _units;
	}

	public List<TransformationSource> getSources() {
		return _sources;
	}
	
	public TransformationSource getSource(FilePort as) {
		for (TransformationSource ts : getSources())
			if ((ts.getPort() == as))
				return ts;
		return null;
	}
	
//	public InstanceDatabase checkDatabase(String metaModelURI) {
//		for (TransformationSource ts: getSources()) {
//			if ((ts.getPort().getMetaModelId().getMetaModelURI().equals(metaModelURI)) && (ts.isProcessed()))
//				return ts.getDatabase();
//		}
//		return null;
//	}
	
	public Map<String, Object> getFactorys() {
		return _factorys;
	}

	public String getClassdir() {
		return _classdir;
	}

	public Map<String, Object> getMetamodels() {
		return _metamodels;
	}

	public InstanceDatabase getLastDatabase(MetaModelDatabase mmd, 
			TransformationLayer layer, String groupName) {

		{
			TransformationLayer ts = (TransformationLayer)layer.getPrecedingUnit();
			do {
				if(ts.getMetaDatabase() == mmd &&
						ts.getGroupName().equals(groupName))
						return ts.getDatabase();
				if(!(ts.getPrecedingUnit() instanceof TransformationLayer))
					break;
				ts = (TransformationLayer)ts.getPrecedingUnit();
			}
			while(ts instanceof TransformationLayer);
		}
		return layer.getDatabase();
	}
	
}
