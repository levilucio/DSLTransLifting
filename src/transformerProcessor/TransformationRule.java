package transformerProcessor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import lifting.PresCondNode;
import transformerProcessor.exceptions.InvalidAttributeRelationException;
import transformerProcessor.exceptions.InvalidLayerRequirement;
import transformerProcessor.exceptions.MissingFeatureException;
import transformerProcessor.filter.AbstractFilter;
import transformerProcessor.filter.ApplyAttributeFilter;
import transformerProcessor.filter.ApplyEntity;
import transformerProcessor.filter.ApplyEntityFilter;
import transformerProcessor.filter.Applyer;
import transformerProcessor.filter.MatchAttributeFilter;
import transformerProcessor.filter.MatchEntityFilter;
import transformerProcessor.filter.MatchFilter;
import dsltrans.AbstractAttributeRelation;
import dsltrans.ApplyAssociation;
import dsltrans.ApplyAttribute;
import dsltrans.ApplyClass;
import dsltrans.ApplyMayBeSameRelation;
import dsltrans.ApplyModel;
import dsltrans.Atom;
import dsltrans.Attribute;
import dsltrans.Import;
import dsltrans.MatchAssociation;
import dsltrans.MatchClass;
import dsltrans.MatchMayBeSameRelation;
import dsltrans.MatchModel;
import dsltrans.PositiveMatchClass;
import dsltrans.Rule;
import dsltrans.Term;
import dsltrans.impl.AttributeEqualityImpl;
import dsltrans.impl.AttributeInequalityImpl;
import emfInterpreter.instance.InstanceAttribute;
import emfInterpreter.instance.InstanceDatabase;
import emfInterpreter.instance.InstanceEntity;
import emfInterpreter.instance.InstanceRelation;
import emfInterpreter.metamodel.DSLTransAttribute;
import emfInterpreter.metamodel.MetaEntity;
import emfInterpreter.metamodel.MetaModelDatabase;

public class TransformationRule {

	private final Rule _rule;
	private List<MatchFilter> _matchFilters;
	private Applyer _applyFilter;
	private boolean _processed;
	private TermProcessor _termprocessor;
	@SuppressWarnings("rawtypes")
	private List<List<Hashtable>> solutionSet;
	// Needed for the lifted version of DSLTrans
	private List<String> _solutionPresenceCondSet;
	private String _currentSolutionPresCond;
	private TransformationLayer _layer=null;

	public TransformationRule(TransformationLayer tl, Rule rule) {
		_rule = rule;
		setLayer(tl);
		_matchFilters = new LinkedList<MatchFilter>();
		setTermProcessor(new TermProcessor(_matchFilters));
		_solutionPresenceCondSet = new LinkedList<String>();

		for (MatchModel mm : rule.getMatch()) {
			MatchFilter mf = new MatchFilter(this,rule, mm);
			if (mm.getExplicitSource() != null) {
				TransformationSource tu = tl.getSource(mm.getExplicitSource());
				if (tu != null) {
					mf.set_explicitMatchMetaModel(tu.getMetaDatabase());
					mf.set_explicitMatchModel(tu.getDatabase());
				}
			}
			
			addMatchFilter(mf);
			// only one match filter exists for all matching sites
			// therefore some matchfilter m.getExistentialIds() returns the ids
			// of the classes that are existentially matched at that matching site
		} 
		
		setApplyFilter(new Applyer(this));	
		setProcessed(false);
	}

	public void MarkTemporalRelations(InstanceDatabase instanceDatabase) {
		// move time forward...
		for(InstanceEntity ie : instanceDatabase.getLoadedClasses())
			ie.setFreshness(false);
	}
	
	public void setMatchFilter(List<MatchFilter> _matchFilter) {
		this._matchFilters = _matchFilter;
	}
	
	public void addMatchFilter(MatchFilter _matchFilter) {
		this._matchFilters.add(_matchFilter);
	}

	public List<MatchFilter> getMatchFilter() {
		return _matchFilters;
	}

	public void setApplyFilter(Applyer _applyFilter) {
		this._applyFilter = _applyFilter;
	}

	public Applyer getApplyFilter() {
		return _applyFilter;
	}
	
	public void clean() {
		for (MatchFilter mf : getMatchFilter()) {
			mf.clean();
		}
	}
	
	class Cartesian<X> {
		public List<List<X>> product(List<List<X>> sets) {
		    if (sets.size() < 2)
		        throw new IllegalArgumentException(
		                "Can't have a product of fewer than two sets (got " +
		                sets.size() + ")");

		    return _product(0, sets);
		}

		private List<List<X>>  _product(int index, List<List<X>> sets) {
			List<List<X>> ret = new LinkedList<List<X>>();
		    if (index == sets.size()) {
		        ret.add(new LinkedList<X>());
		    } else {
		        for (X obj : sets.get(index)) {
		            for (List<X> set : _product(index+1, sets)) {
		                set.add(0,obj);
		                ret.add(set);
		            }
		        }
		    }
		    return ret;
		}
	};
	
	
	@SuppressWarnings("rawtypes")
	public boolean performMatch(
			TransformationController control,
			InstanceDatabase matchModel, 
			InstanceDatabase applyModel,
			MetaModelDatabase matchMetaModel,
			MetaModelDatabase applyMetaModel,
			Hashtable<String,String> presenceConds,
			String featureFormula) throws Throwable {
		
		System.out.println("Processing rule: " + this.toString());
		
		// the solutions from matching a rule on a model are only looked for once (and the isProcessed flag is set to True)
		// although the performMatch method is called from the executeRules method of TransformationLayer as long as
		// all the solutions found for the rule in the given model have not been exhausted.
		if(!isProcessed()) { // initial queries
			List<List<Hashtable>> results = new LinkedList<List<Hashtable>>();
			for (MatchFilter mf : getMatchFilter()) {
				
				if(mf.process(control,matchModel,applyModel,matchMetaModel,applyMetaModel,getTermProcessor(),presenceConds,featureFormula)) {
					results.add(mf.getBindingList());
					
					// print out how which match class ids correspond to which objects in the source model
					for(Hashtable h :mf.getBindingList()) {
						System.out.println("MATCH CLASS IDS: " + h.keySet().toString());
						System.out.println("OBJECT IDS:" + h.values().toString()); 
					}
					
					if(!mf.getExistentialIDs().isEmpty())
						System.out.println("EXISTENTIAL IDS: " + mf.getExistentialIDs().toString());
					
					if(!mf.getNegativeIDs().isEmpty())
						System.out.println("NEGATIVE IDS: " + mf.getNegativeIDs().toString());
				} else
					return false; //1 match failed, no need to continue
			}
			
			if(results.size() > 1) {
				solutionSet = new Cartesian<Hashtable>().product(results);
			} else {
				solutionSet = new LinkedList<List<Hashtable>>();
				for(Hashtable result: results.get(0)) {
					List<Hashtable> one = new LinkedList<Hashtable>();
					solutionSet.add(one);
					one.add(result);
				}
			}
			
			System.out.println("SOLUTION SET SIZE: " + solutionSet.size() + "  RESULTS SIZE: " + results.size());
			System.out.println("MATCH FILTER COUNT: " + getMatchFilter().size());
			
			// lift the existential matcher here, by associating to each existential match its presence conditions coming from the matcher
			// add to the TransformationRule class a string containing the presence condition for the current solution
			
			// build first a list (in the same order as the 'results' list) associating the ANDed presence conditions to each individual solution
			
			// add stuff here so that for existential case (multiple matchers) 
			// try adding a root element
			
			String solutionPresenceCond = null;
			String liftedSolutionPresenceCond = null;
			List<Hashtable> processedResults = new ArrayList<Hashtable>();
			List<String> pastSolutionPC = new ArrayList<String>(); 
			
			List<String> differAcrossExistentialMatchIds = new ArrayList<String>();
			// using the first match filter below to get existential ids and backward link ids should be
			// fine because those associated with the first set of results
			differAcrossExistentialMatchIds.addAll(getMatchFilter().get(0).getExistentialIDs());
			differAcrossExistentialMatchIds.addAll(getMatchFilter().get(0).getBackwardLinkIDs());
			
			Hashtable<String, String> positiveIndirectIds = getMatchFilter().get(0).getPositiveIndirectIDs();
			
			for (Hashtable result: results.get(0)) {
				Enumeration matcherIds = result.keys();

				while (matcherIds.hasMoreElements()) {
					String matcherId = (String)matcherIds.nextElement();
					String emfObjectID = (result.get(matcherId)).toString();
					String localPresCond = presenceConds.get(emfObjectID.substring(1));
					
					// if the object is the target of an indirect link, then the presence conditions of all elements
					// from the source to the target of the link have to be conjoined
					if(positiveIndirectIds.containsKey(matcherId)) {
						String indirectPresCond = "";
						// start from the target, not the source, because each element can be the target of at most one containment association
						// it can be the source of many though
						String sourceObjectId = (result.get(positiveIndirectIds.get(matcherId))).toString().substring(1);
						String targetObjectId = emfObjectID.substring(1);
						
						// the target object changes as each target element is found; each new target is at a lesser depth than the previous
						// until eventually the source element being searched for is found
						while(!sourceObjectId.equals(targetObjectId)) {
							for(InstanceRelation ir : matchModel.getLoadedRelations()) {
								if(ir.getRelation().isContainment() && Integer.toString(ir.getTarget().hashCode()).equals(targetObjectId)) {
									// along the way, the presence conditions of the in-between object and association are included
									targetObjectId = Integer.toString(ir.getSource().hashCode());
									indirectPresCond += presenceConds.get(Integer.toString(ir.hashCode())) + " ";
									indirectPresCond += presenceConds.get(targetObjectId) + " ";
									break;
								}
							}
						}
						
						if(!indirectPresCond.isEmpty())
							localPresCond = "(and " + localPresCond + " " + indirectPresCond.substring(0, indirectPresCond.length() - 1) + ")";
					}
					if (localPresCond != null) {
						if (solutionPresenceCond == null) {
							solutionPresenceCond = new String(localPresCond);
						} else {	
							solutionPresenceCond = "(and " + localPresCond + " " + solutionPresenceCond + ")";
						}
					}
				}
				
				// clause representing all the object combinations that satisfy the NAC
				String negativeComponent = getMatchFilter().get(0).getSolutionsNegative().get(results.get(0).indexOf(result));
				
				if(!negativeComponent.isEmpty()) 
					solutionPresenceCond = "(and " + solutionPresenceCond + " " + negativeComponent + ")";
				
				// where existential matches are lifted
				String liftedPC = null;
				boolean sameExistentialMatchSet;
				
				// going over the binding lists is analogous to going over the list of processed matches 
				// all matches are treated as universal before this point
				for(Hashtable bindingList : processedResults ) {
					sameExistentialMatchSet = true;
					
					// if some differing class ID that is not one of the things that is existentially matched 
					// and that differing ID is also not a backward link
					// then that is another universal match site and we don't have to consider it
					for(Object mc : bindingList.keySet()) {
						if(!bindingList.get((String)mc).toString().equals(result.get((String)mc).toString())) {
							if(!differAcrossExistentialMatchIds.contains((String)mc)) {
								sameExistentialMatchSet = false;
								//System.out.println((String)mc);
							}
						}
					}
					 
					if(sameExistentialMatchSet) {
						String otherExistentialCond = pastSolutionPC.get(processedResults.indexOf(bindingList));
						liftedPC = (liftedPC == null)? "(not " + otherExistentialCond + ")" : "(and " + liftedPC + " (not " + otherExistentialCond + "))";
					}

				}
				
				liftedSolutionPresenceCond = (liftedPC != null) ? "(and " + solutionPresenceCond + " " + liftedPC + ")" : solutionPresenceCond;
				
				// simplify the presence condition
				if(liftedSolutionPresenceCond != null)
					liftedSolutionPresenceCond = PresCondNode.extract(liftedSolutionPresenceCond).condense().toString();
				
				// these are two separate lists because there were implementation difficulties with hastables
				processedResults.add(result);
				pastSolutionPC.add(solutionPresenceCond); 
				
				// the unlifted presence condition is added to the pastSolutionPC list 
				// because the lifted version is not required for modifying the presence conditions of the other matches
				// Note that using the unlifted version would not be wrong; it would just increase the clause-to-variable ratio
				_solutionPresenceCondSet.add(liftedSolutionPresenceCond); 
				//solutionPresenceCond matches the current solution presence condition in the applyer class
				
				//number of not clauses should increase as existential matches are processed
				System.out.println("SOLUTION PRESENCE CONDITION: " + liftedSolutionPresenceCond);
				solutionPresenceCond = null;
				liftedSolutionPresenceCond = null;
			}
			
			setProcessed(true);
		}
		
		if(solutionSet.size() == 0) return false; // no more solutions
		
		
		
		boolean validSolution;
		do {
			validSolution = true;
			List<Hashtable> solution = solutionSet.get(0);
			solutionSet.remove(0); // remove the head of the list of the solutions 
			_currentSolutionPresCond = _solutionPresenceCondSet.remove(0); // remove the head of the ANDed presence conditions
											   							 // for each of the positive elements in the match set
			// LEVI: HACK FOR MATCHES WHERE NO ELEMENT HAS PRESENCE CONDITIONS
			if (_currentSolutionPresCond == null) _currentSolutionPresCond = "true";
			
			for(int i = 0; i < getMatchFilter().size() && validSolution; i++) {
				MatchFilter mf = getMatchFilter().get(i);
				System.out.println("try rule again: " + this.toString());
				mf.updateFilters(solution.get(i),matchModel, applyModel);
				validSolution = mf.isValid();
			}
			
			// Aditional check for inter match filter relations
			if (validSolution) {
				/*
				 * If the solution is still valid, check if the existing 
				 * attribute relations are being obeyed.
				 */
				for (AbstractAttributeRelation aatr: getRule().getAttributeRelations()) {
					if (!isObeyed(aatr)){
						validSolution = false;
						break;
					}
				}
			}
			
		} while (!validSolution && hasMoreSolutions());
		
		
		if (!validSolution) {
			assert !hasMoreSolutions();
			return false;
		}
		
		// fill out the rule filter with all solutions for elements that are backward linked
		
		for(int i = 0; i < getMatchFilter().size(); i++) {
			MatchFilter mf = getMatchFilter().get(i);
			mergeApplyFilters(mf);
		}
		
//		System.out.println("Number of entities in the applyfilter: ");
//		System.out.println(this.getApplyFilter().getEntities().size());
		
		return true; //all matches passed
	}

	private boolean isObeyed(AbstractAttributeRelation aatr) throws InvalidAttributeRelationException, MissingFeatureException {
		/*
		 * his method assumes all the matchfilters have been filled with 
		 * the corresponding bindings.
		 */
		
		Attribute source = aatr.getSourceAttribute();
		Attribute target = aatr.getTargetAttribute();
		
		MatchAttributeFilter source_maf = null;
		ApplyAttributeFilter source_aaf = null;
		MatchAttributeFilter target_maf = null;
		ApplyAttributeFilter target_aaf = null;
		
	
		 // There can be all combinations of attribute filters 
		 // corresponding to the source and target attributes.
		
		
		source_maf = findMatchAttributeFilter(source);
		if (source_maf==null) {
			// The attribute relation doesn't start in the match model.
			// It must be in the apply model. Or else it's an error...
			source_aaf = findApplyAttributeFilter(source);
			if (source_aaf == null) {
				// probably the attribute relation connects attributes 
				// from different rules, or different layers
				throw new InvalidAttributeRelationException("Attribute relations have to be contained in a single rule.", aatr);
			}
		}
		assert source_maf!=null || source_aaf != null;
		
		target_maf = findMatchAttributeFilter(target);
		if (target_maf==null) {
			// The attribute relation doesn't start in the match model.
			// It must be in the apply model. Or else it's an error...
			target_aaf = findApplyAttributeFilter(target);
			if (target_aaf == null) {
				// probably the attribute relation connects attributes 
				// from different rules, or different layers
				throw new InvalidAttributeRelationException("Attribute relations have to be contained in a single rule.", aatr);
			}
		}
		assert target_maf!=null || target_aaf != null;
		
		// Exactly one of the following combinations will hold.
		assert (source_maf!=null && target_maf!=null) 
				|| (source_maf!=null && target_aaf!=null)
				|| (source_aaf!=null && target_maf!=null)
				|| (source_aaf!=null && target_aaf!=null);
		
		if (source_maf!=null && target_maf!=null) {
			// One of these must hold.
			if (aatr instanceof AttributeEqualityImpl) {
				return (source_maf.getCurrentAttribute().getValue().equals(target_maf.getCurrentAttribute().getValue()));
			}
			if (aatr instanceof AttributeInequalityImpl) {
				return !(source_maf.getCurrentAttribute().getValue().equals(target_maf.getCurrentAttribute().getValue()));
			}
			throw new MissingFeatureException("Cannot recognize new AbstractAttributeRelation.");
		}
		if (source_maf!=null && target_aaf!=null) {
			// One of these must hold.
			if (aatr instanceof AttributeEqualityImpl) {
				return (source_maf.getCurrentAttribute().getValue().equals(target_aaf.getCurrentAttribute().getValue()));
			}
			if (aatr instanceof AttributeInequalityImpl) {
				return !(source_maf.getCurrentAttribute().getValue().equals(target_aaf.getCurrentAttribute().getValue()));
			}
			throw new MissingFeatureException("Cannot recognize new AbstractAttributeRelation.");
		}
		if (source_aaf!=null && target_maf!=null) {
			// One of these must hold.
			if (aatr instanceof AttributeEqualityImpl) {
				return (source_aaf.getCurrentAttribute().getValue().equals(target_maf.getCurrentAttribute().getValue()));
			}
			if (aatr instanceof AttributeInequalityImpl) {
				return !(source_aaf.getCurrentAttribute().getValue().equals(target_maf.getCurrentAttribute().getValue()));
			}
			throw new MissingFeatureException("Cannot recognize new AbstractAttributeRelation.");
		}
		if (source_aaf!=null && target_aaf!=null) {
			// One of these must hold.
			if (aatr instanceof AttributeEqualityImpl) {
				return (source_aaf.getCurrentAttribute().getValue().equals(target_aaf.getCurrentAttribute().getValue()));
			}
			if (aatr instanceof AttributeInequalityImpl) {
				return !(source_aaf.getCurrentAttribute().getValue().equals(target_aaf.getCurrentAttribute().getValue()));
			}
			throw new MissingFeatureException("Cannot recognize new AbstractAttributeRelation.");
		}
		
		// It is bad to get here...
		throw new InvalidAttributeRelationException("Attribute relations have to be contained in a single rule.", aatr);
	}

	private boolean hasMoreSolutions() {
		return !solutionSet.isEmpty();
	}
	

	private void mergeApplyFilters(MatchFilter mf) {
		for(ApplyEntityFilter maef: mf.getApplyEntityFilters()) {
			for(ApplyEntity ae : this.getApplyFilter().getEntities()) {
				if(ae.getApplyClass() == maef.getApplyClass()) {
					System.out.println("filled " + ae.getDotNotation() +" with " + maef.getDotNotation());
					ae.setCurrentEntity(maef.getCurrentEntity());
					ae.setPastEntity(true);
					
				}
			}
		}
	}

	private List<InstanceEntity> copyChildren(InstanceDatabase inputModel, InstanceDatabase outputModel, InstanceEntity rootIn, MetaModelDatabase applyMetaModel) {
		List<InstanceEntity> entities = new LinkedList<InstanceEntity>();
		outputModel.addEntity(rootIn);
		outputModel.createTemporalRelation(rootIn,rootIn);
		if(!rootIn.getTemporalChildren().contains(rootIn))
			rootIn.getTemporalChildren().add(rootIn);
		
		entities.add(rootIn);
		for (InstanceAttribute ia : inputModel.getAttributesByInstanceEntity(rootIn))
			outputModel.addAttribute(ia);
		
		for (InstanceRelation ir :inputModel.getRelationsByInstanceEntity(rootIn)) {
			if (ir.getRelation().isContainment()) {
				MetaEntity me = ir.getTarget().getMetaEntity();
				try {
					MetaEntity meA = applyMetaModel.getMetaEntityByName(me.getNamespace(), me.getName());
					ir.getTarget().setMetaEntity(meA);
				} catch (InvalidLayerRequirement e) {
					e.printStackTrace();
				}
				outputModel.addRelation(new InstanceRelation(rootIn, ir.getRelation(), ir.getTarget()));	
				entities.addAll(copyChildren(inputModel, outputModel, ir.getTarget(), applyMetaModel));
			}
		}
		return entities;
	}
	
	private void fillNonContainments(InstanceDatabase inputModel, InstanceDatabase outputModel, List<InstanceEntity> ies) {
		for (InstanceEntity ie : ies) {
			for (InstanceRelation ir : inputModel.getRelationsByInstanceEntity(ie)) {
				if (!ir.getRelation().isContainment()) {
					if (ies.contains(ir.getSource()) && ies.contains(ir.getTarget()))
						outputModel.addRelation(new InstanceRelation(ir.getSource(), ir.getRelation(), ir.getTarget()));
				}
			}
		}
	}
	
	
	public void performApply(InstanceDatabase instanceDatabase, MetaModelDatabase applyMetaModel,
							 InstanceDatabase matchModel, Hashtable<String,String> presenceConds)
			throws InvalidLayerRequirement, SecurityException, IllegalArgumentException,
			ClassNotFoundException, NoSuchFieldException, IllegalAccessException,
			NoSuchMethodException, InvocationTargetException {
		// apply the transformation
		
		getApplyFilter().performApply(instanceDatabase, applyMetaModel, matchModel, getCurrentSolutionPresCond(), presenceConds);
		processMappings();
		instantiateTemporalRelations(instanceDatabase);
		getApplyFilter().updateFilters();
	}

	private void instantiateTemporalRelations(InstanceDatabase instanceDatabase) {
		// instantiate Temporal Relations (history)
		for (MatchFilter mf : this.getMatchFilter()) {
			for(MatchEntityFilter mef : mf.getMatchEntityFilters()) {
				if(MatchFilter.isPositive(mef.getMatchClass())) {
					for(ApplyEntity ae: this.getApplyFilter().getEntities()) {
						boolean hasDefault = false;
						for(ApplyAttributeFilter aaf : ae.getFilterAttributes()) {
							if(aaf.getName().equals(DSLTransAttribute.DSLTRANS_DEFAULT)) {
								hasDefault = true;
								break;
							}
						}
						if(!ae.isPastEntity() && !ae.isImported() && hasDefault) {
							instanceDatabase.createTemporalRelation(mef.getCurrentEntity(),ae.getCurrentEntity());
						}
					}
				}
			}
		}
	}

	private void processMappings() {
		
		// process attribute mappings
		for (ApplyClass ac : getRule().getApply().getClass_()) {
			AbstractFilter af = getApplyFilter().getFilter(ac);
			ApplyEntityFilter aef = (ApplyEntityFilter)af;
			for (ApplyAttribute at : ac.getAttribute()) {
				for (ApplyAttributeFilter aaf : aef.getFilterAttributes()) {
					if (aaf.getAttribute().equals(at)) {
						Term attValue = at.getAttributeValue();
						String attributeValue;
						
						//Attribute might've been processed through another's reference
						if (getTermProcessor().hasTerm(attValue))
							attributeValue = getTermProcessor().getTerm(attValue);
						else
							attributeValue = getTermProcessor().processTerm(at.getAttributeValue(), getRule());
						
						InstanceAttribute ia = aaf.getCurrentAttribute();
						if (ia == null)
							break;
						if (attributeValue.isEmpty())
							attributeValue = ia.getMetaAttribute().getAttribute().getDefaultValueLiteral();
						if (attributeValue != null) {
							if (ia.getMetaAttribute().getType().equals("int"))
								aaf.getCurrentAttribute().setValue(Integer.parseInt(attributeValue));
							else
								aaf.getCurrentAttribute().setValue(attributeValue);
						}
					}
				}
			}
		}
		getTermProcessor().Clear();
	}
	
	private MatchAttributeFilter findMatchAttributeFilter(Attribute a) {
		// Percorre todos os MatchFilters e em cada um deles procura o MatchAttributeFilter e devolve o primeiro que encontrar.
		
		MatchAttributeFilter result = null;
		
		for (MatchFilter mf : getMatchFilter()) {
			result = mf.findMatchAttributeFilter(a);
			if (result != null) {
				return result;
			}
		}
		assert result == null;
		return null;
	}
	
	private ApplyAttributeFilter findApplyAttributeFilter(Attribute a) {
		// Percorre todos os MatchFilters e em cada um deles procura o ApplyAttributeFilter e devolve o primeiro que encontrar.
		
		ApplyAttributeFilter result = null;
		
		for (MatchFilter mf : getMatchFilter()) {
			result = mf.findApplyAttributeFilter(a);
			if (result != null) {
				return result;
			}
		}
		assert result == null;
		return null;
	}

	public void processImports(ApplyEntityFilter aef, InstanceDatabase instanceDatabase,
			MetaModelDatabase applyMetaModel, InstanceDatabase matchModel) {
		//process imports
		Import imp = null;
		ApplyClass ac = aef.getApplyClass();
		for(Import sr : this.getRule().getImports()) {
			if(sr.getTarget() == ac) {
				for (MatchModel mm : this.getRule().getMatch()){
					if(mm.getExplicitSource() != null && mm.getClass_().contains(sr.getSource())) {
						imp = sr;
						break;
					}
				}
			}
			if(imp != null)
				break;
		}
		if(imp == null) return;
		
		//get InstanceEntity from MatchModel - old root
		InstanceEntity ie = null;
		@SuppressWarnings("unused")
		MatchFilter currMatch = null;
		{
			MatchClass match = imp.getSource();
			AbstractFilter af = null;
			for (MatchFilter mf : getMatchFilter()) {
				currMatch = mf;
				af = mf.getFilter(match);
				if (af != null)
					break;
			}
			if(af != null && match instanceof PositiveMatchClass) {
				MatchEntityFilter mef = (MatchEntityFilter) af;
				ie = mef.getCurrentEntity();
			}
		}

		InstanceDatabase input = null;
		for (MatchFilter mf : getMatchFilter()) {
			if (mf.get_matchModel().getClass_().contains(imp.getSource()) &&
					mf.get_explicitMatchModel() != null)
				input = mf.get_explicitMatchModel();
		}
		if(input == null) return;
			
		//copy all children to output and link with new root
		List<InstanceEntity> entitiesCreated = copyChildren(input, instanceDatabase, ie, applyMetaModel);
		
		//fill non containment relations between subtree copied 
		fillNonContainments(input, instanceDatabase, entitiesCreated);

		// instantiate Temporal Relations (history)
		for (MatchFilter mf : this.getMatchFilter()) {
			for(MatchEntityFilter mef : mf.getMatchEntityFilters()) {
				if(MatchFilter.isPositive(mef.getMatchClass())) {
					//for(InstanceEntity ieC : entitiesCreated) {
					instanceDatabase.createTemporalRelation(mef.getCurrentEntity(),ie);
					if(!mef.getCurrentEntity().getTemporalChildren().contains(ie))
						mef.getCurrentEntity().getTemporalChildren().add(ie);
					//}
				}
			}
		}
		
		for(ApplyAttributeFilter aaf : aef.getFilterAttributes()) {
			if(aaf.getName().equals(DSLTransAttribute.DSLTRANS_DEFAULT)) {
				for(InstanceEntity newentity: entitiesCreated) {
					InstanceAttribute ia;
					try {
						ia = new InstanceAttribute(newentity,applyMetaModel.getMetaAttributeByName(DSLTransAttribute.DSLTRANS_DEFAULT));
						Term term = aaf.getAttribute().getAttributeValue();
						ia.setValue(((Atom)term).getValue());
						instanceDatabase.addAttribute(ia);
						aaf.setCurrentAttribute(ia);
					} catch (InvalidLayerRequirement e) {
						e.printStackTrace();
					}
				}
				break; // only one per instance
			}
		}
		
		aef.setCurrentEntity(ie);
	}
	
	public Rule getRule() {
		return _rule;
	}

	public void setProcessed(boolean _processed) {
		this._processed = _processed;
	}

	public boolean isProcessed() {
		return _processed;
	}

	public void setTermProcessor(TermProcessor _termprocessor) {
		this._termprocessor = _termprocessor;
	}

	public TermProcessor getTermProcessor() {
		return _termprocessor;
	}
	
	public String getCurrentSolutionPresCond() {
		return _currentSolutionPresCond;
	}

	public String setCurrentSolutionPresCond(String currentSolutionPresCond) {
		return currentSolutionPresCond = _currentSolutionPresCond;
	}	
	
	public boolean hasImportClass(ApplyClass mc) {
		for(Import sr : this.getRule().getImports()) {
			if(sr.getTarget() == mc)
				return true;
		}
		return false;
	}

	void setLayer(TransformationLayer _layer) {
		this._layer = _layer;
	}

	public TransformationLayer getLayer() {
		return _layer;
	}

	public boolean hasMayBeSameRelation(MatchClass mc_i, MatchClass mc_j) {
		
		/*
		 * Look in every association that is a MayBeSameRelation {
		 *  if the source and/or target correspond to the mc_i and mc_j then {
		 *   return true.
		 *  }
		 * }
		 * return false if none is found
		 * 
		 */
		
		for (MatchModel mm: getRule().getMatch()) {
			for (MatchAssociation ma: mm.getAssociation()) {
				if (ma instanceof MatchMayBeSameRelation) {
					if ( (mc_i == ma.getSource() && mc_j == ma.getTarget()) ||
							( mc_j == ma.getSource() && mc_i == ma.getTarget() )) {
						return true;
					}
				}
			}
		}
		
		return false;
	}

	public boolean hasMayBeSameRelation(ApplyClass ac_i, ApplyClass ac_j) {
		
		/*
		 * Look in every association that is a MayBeSameRelation {
		 *  if the source and/or target correspond to the mc_i and mc_j then {
		 *   return true.
		 *  }
		 * }
		 * return false if none is found
		 * 
		 */
		
		ApplyModel mm = getRule().getApply();
		for (ApplyAssociation ma: mm.getAssociation()) {
			if (ma instanceof ApplyMayBeSameRelation) {
				if ( (ac_i == ma.getSource() && ac_j == ma.getTarget()) ||
						( ac_j == ma.getSource() && ac_i == ma.getTarget() )) {
					return true;
				}
			}
		}
		
		return false;
	}

	@Override
	public String toString() {
		return _rule.getDescription();
	}
	
	
	
}
