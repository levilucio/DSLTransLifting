package transformerProcessor.filter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import jpl.Atom;
import jpl.Query;
import jpl.Util;
import jpl.Term;

// Z3 solver
import solver.Z3Solver;
import solver.Z3Model.Z3Bool;
import transformerProcessor.TermProcessor;
import transformerProcessor.TransformationController;
import transformerProcessor.TransformationRule;
import transformerProcessor.exceptions.InvalidLayerRequirement;

import com.sun.tools.javac.util.Pair;

import dsltrans.AbstractAttributeRelation;
import dsltrans.AbstractTemporalRelation;
import dsltrans.AnyMatchClass;
import dsltrans.ApplyClass;
import dsltrans.Attribute;
import dsltrans.ExistsMatchClass;
import dsltrans.MatchAssociation;
import dsltrans.MatchClass;
import dsltrans.MatchMayBeSameRelation;
import dsltrans.MatchModel;
import dsltrans.NegativeBackwardRestriction;
import dsltrans.NegativeIndirectAssociation;
import dsltrans.NegativeMatchAssociation;
import dsltrans.PositiveBackwardRestriction;
import dsltrans.PositiveIndirectAssociation;
import dsltrans.PositiveMatchAssociation;
import dsltrans.PositiveMatchClass;
import dsltrans.Rule;
import dsltrans.impl.AttributeEqualityImpl;
import dsltrans.impl.AttributeInequalityImpl;
import emfInterpreter.instance.InstanceAttribute;
import emfInterpreter.instance.InstanceDatabase;
import emfInterpreter.instance.InstanceEntity;
import emfInterpreter.instance.InstanceRelation;
import emfInterpreter.metamodel.MetaModelDatabase;

public class MatchFilter {
	private final List<MatchEntityFilter> _matchentityFilters;
	private final List<ApplyEntityFilter> _applyentityFilters;	
	private final List<MatchRelationFilter> _matchrelationFilters;
	private final List<TemporalRelationFilter> _temporalrelationFilters;
	private final InstanceDatabase _FilterDatabase;	
	private MetaModelDatabase _explicitMatchMetaModel;
	private InstanceDatabase _explicitMatchModel;
	final String entityFact = "entity";
	final String relationFact = "relation";
	final static String DIFF_ATTR_FACT = "allDifferent";
	private String _positiveJoinHead = "";
	private String _positiveJoinBody = "";
	private String _positiveJoinHead2 = "";
	private String _positiveJoinBody2 = "";
	private String _cutPredicate = "";
	private String _query;
	private MatchModel _matchModel;
	@SuppressWarnings("rawtypes")
	private List<Hashtable> _binding;
	private List<QueryRelation> _positiveRelations;
	private String _queryCutHead="";
	private String _queryHead="";
	private int _temporalCounter=0;
	private int _applyCounter=0;
	private String _differentAttrFact= "";
	private String _differentAttrFactHead = "";
	private TransformationRule _transformationRule;
	
	// existential IDs is needed to pass to the rewriter the IDs of the objects that were
	// ids of existential matchers
	private List<String> _existentialIds = null; 
	// ids of negative matchers
	private List<String> _negativeIds = null; 
	private List<String> _negativeClassIds = null; 
	String _negativeJoinPredicate = "";
	// ids of backward link matchers
	private List<String> _backwardLinkIds = null; 
	// each entry is a list of presence conditions belonging to the objects that were negatively 
	// matched in the solution (i.e. to be negated in the solution presence condition)
	private List<String> _solutionsNegativeComponent = null; 
	// treat negative matchers as positive
	private boolean treatNegativeAsPositive = false;
	// ids of indirect source and target match classes
	private Hashtable<String, String> _positiveIndirectIds = null;
	private Hashtable<String, String> _negativeIndirectIds = null;
	// each entry is a list of presence conditions belonging to the in-between objects and associations
	// from a negative indirect link (i.e. to be negated in the solution presence condition)
	private List<String> _solutionsNegativeIndirectComponent = null; 
	
	
	private class Clause {
		Clause(String clause) {
			if(!clause.isEmpty()) {
				System.out.println(clause+".");
				new Query(Util.textToTerm("assert("+clause+")")).oneSolution();
			}
		}
	};	
	
	private class QueryRelation {
		private String relationName = "";
		private String query = "";
		
		public QueryRelation(String relationname, String actualquery) {
			setRelationName(relationname);
			setQuery(actualquery);
		}

		public void setRelationName(String relationName) {
			this.relationName = relationName;
		}

		public String getRelationName() {
			return relationName;
		}

		public void setQuery(String query) {
			this.query = query;
		}

		public String getQuery() {
			return query;
		}
	}
	
	@SuppressWarnings("rawtypes")
	public MatchFilter(TransformationRule tr, Rule rule, MatchModel mm) {
		_FilterDatabase = new InstanceDatabase();
		_matchentityFilters = new LinkedList<MatchEntityFilter>();
		_matchrelationFilters = new LinkedList<MatchRelationFilter>();
		_applyentityFilters = new LinkedList<ApplyEntityFilter>();
		_temporalrelationFilters = new LinkedList<TemporalRelationFilter>();
		_existentialIds = new ArrayList<String>();
		_negativeIds = new ArrayList<String>();
		_negativeClassIds = new ArrayList<String>();
		_backwardLinkIds = new ArrayList<String>();
		_positiveIndirectIds = new Hashtable<String, String>();
		_negativeIndirectIds = new Hashtable<String, String>();
		_solutionsNegativeComponent = new ArrayList<String>();
		_solutionsNegativeIndirectComponent = new ArrayList<String>();
		_binding = new LinkedList<Hashtable>();
		_transformationRule = tr;
		setPositiveRelations(new LinkedList<QueryRelation>());
		set_matchModel(mm);
		set_explicitMatchMetaModel(null);
		set_explicitMatchModel(null);
			
		int id = 0;
		
		for (MatchClass mc : mm.getClass_()) {
			getMatchEntityFilters().add(new MatchEntityFilter(tr,mc,"Id"+Integer.toString(id++)));
			
			MatchEntityFilter last = getMatchEntityFilters().get(getMatchEntityFilters().size() - 1);
			System.out.println(last.getDotNotation() + " ID: " + last.getId() + " MATCH CLASS: " + last.getMatchClass());
		
			// add the existential matchers' class IDs
			if(isExistentialClass(mc)) {
				getExistentialIDs().add("Id" + Integer.toString(id - 1));
			}
			
			// keep track of the negative class matchers
			if(isNegativeClass(mc)) {
				getNegativeIDs().add("Id" + Integer.toString(id - 1));
				_negativeClassIds.add("Id" + Integer.toString(id - 1));
			}
		}
		
		List<ApplyClass> ac = new LinkedList<ApplyClass>();
		for(AbstractTemporalRelation br : rule.getBackwards()) {
			if(mm.getClass_().contains(br.getSourceClass()) // referent to this match model
					&& !ac.contains(br.getTargetClass())) { // gather unique apply class
				getApplyEntityFilters().add(new ApplyEntityFilter(tr,br.getTargetClass(),"Id"+Integer.toString(id++)));
				ac.add(br.getTargetClass());
			}
			
			// don't need to add apply class IDs to either the list of existential ids or backward link ids
			ApplyEntityFilter last = getApplyEntityFilters().get(getApplyEntityFilters().size() - 1);
			System.out.println(last.getDotNotation() + " ID: " + last.getId() + " APPLY CLASS: " + last.getApplyClass());
		}
		
		for (MatchAssociation ma : mm.getAssociation()) {
			if ( shouldBeBinded(ma) ) {
				getMatchRelationFilters().add(new MatchRelationFilter(ma,"Id"+Integer.toString(id++)));
			}
			
			MatchRelationFilter last = getMatchRelationFilters().get(getMatchRelationFilters().size() - 1);
			System.out.println(ma.toString() + " ID: " + last.getId() + " Source CLASS: " + last.getAssociation().getSource() + " TARGET CLASS: " + last.getAssociation().getTarget());
		
			// add the existentially matched association IDs
			if(isExistentialClass(ma.getSource()) || isExistentialClass(ma.getTarget())) {
				// NOTE: id - 1 is the id of the last object added (because of the id++ operation)
				getExistentialIDs().add("Id" + Integer.toString(id - 1));
			}
			
			// keep track of the negative association matchers
			if(isNegativeAssociation(ma) || isNegativeIndirect(ma)) {
				getNegativeIDs().add("Id" + Integer.toString(id -1));
			}
			
			// keep track of matcher ids in indirect links
			// done by finding the ids of the match entity filters that correspond to the source and target match classes
			if(isIndirect(ma)) {
				String target = getIdFrom(last.getAssociation().getTarget());
				String source = getIdFrom(last.getAssociation().getSource());

				if(!target.isEmpty() && !source.isEmpty()) {
					if(isPositiveIndirect(ma)) {
						_positiveIndirectIds.put(target, source);
					} else if(isNegativeIndirect(ma)) {
						_negativeIndirectIds.put(target, source);
					}
				} 
			}
		}
			
		for(AbstractTemporalRelation br : rule.getBackwards()) {
			if(mm.getClass_().contains(br.getSourceClass())) {// referent to this match model
				getTemporalFilters().add(new TemporalRelationFilter(br,"Id"+Integer.toString(id++)));
			}
			
			// add the backward links IDs, which should also vary across existential matches
			TemporalRelationFilter last = getTemporalFilters().get(getTemporalFilters().size() - 1);
			System.out.println(last.toString() + " ID: " + last.getId() + " SOURCE CLASS: " + last.getSourceClass() + "TARGET CLASS: " + last.getTargetClass());
			// NOTE: id - 1 is the id of the last object added (because of the id++ operation)
			getBackwardLinkIDs().add("Id" + Integer.toString(id - 1));
		}
		buildJoinPredicates();
	}

	private boolean shouldBeBinded(MatchAssociation ma) {
		return !(ma instanceof MatchMayBeSameRelation);
	}

	public InstanceDatabase getFilterDatabase() {
		return _FilterDatabase;
	}
	
	public int getNumberFilters() {
		return getMatchRelationFilters().size() + getMatchEntityFilters().size();
	}
	
	public List<MatchEntityFilter> getMatchEntityFilters() {
		return _matchentityFilters;
	}

	public List<MatchRelationFilter> getMatchRelationFilters() {
		return _matchrelationFilters;
	}

	public void clean() {
		new Query(Util.textToTerm("retractall(entity(_,_,_,_,_))")).allSolutions();
		new Query(Util.textToTerm("retractall(relation(_,_))")).allSolutions();
		if(!getQueryHead().isEmpty()) {
			new Query(Util.textToTerm("retractall(queryjoin("+getQueryHead()+"))")).allSolutions();
			new Query(Util.textToTerm("retractall(nqueryjoin("+getQueryHead()+"))")).allSolutions();
		}
		if(!getQueryCutHead().isEmpty()) {
			new Query(Util.textToTerm("retractall(cutclause("+getQueryCutHead()+"))")).allSolutions();
		}
		if(!getQueryDifferentEntitiesHead().isEmpty()) {
			new Query(Util.textToTerm("retractall("+ getQueryDifferentEntitiesHead() + ")")).allSolutions();
		}
		setQueryHead("");
		setQueryCutHead("");
		
		String factString = entityFact + "(0,'',[],[0],[0])";						
		new Clause(factString);
		factString = relationFact + "(0,[])";
		new Clause(factString);
	}
	
	private void setQueryDifferentEntitiesFact(String string) {
		_differentAttrFact = string;
	}

	private void setQueryDifferentEntitiesHead(String string) {
		_differentAttrFactHead = string;
	}

	private String getQueryDifferentEntitiesFact() {
		return _differentAttrFact;
	}

	private String getQueryDifferentEntitiesHead() {
		return _differentAttrFactHead;
	}

	/*
	 * Perform partial queries
	 * and then join the results using a prolog engine
	 * */
	public boolean process(TransformationController control,InstanceDatabase matchModel,InstanceDatabase applyModel,
			MetaModelDatabase matchMetaModel, MetaModelDatabase applyMetaModel, 
			TermProcessor tp, Hashtable<String,String> presenceConds, String featureFormula) throws Throwable {
			
//		System.out.println("--------------------------------------------------------");
//		System.out.println("Feature formula: " + featureFormula);
//		System.out.println();
//		System.out.println("Presence conditions: "); 
//		Enumeration<String> e = presenceConds.keys();
//		while (e.hasMoreElements()) {
//			String id = (String) e.nextElement();
//			for (InstanceEntity ie: matchModel.getLoadedClasses()) {
//				//System.out.println(Integer.toString(ie.hashCode()) + " : " + id);
//				if (Integer.toString(ie.hashCode()).equals(id)) {
//					String name = null;
//					for(InstanceAttribute ia: matchModel.getAttributesByInstanceEntity(ie)){
//						if (ia.getMetaAttribute().getName().equals("Name"))
//							name = ia.getValue().toString();
////					System.out.println(id + ": " + name);
//					}
//				}
//			}
//			for (InstanceRelation ir: matchModel.getLoadedRelations()) {
//				//System.out.println(Integer.toString(ir.hashCode()) + " : " + id);
//				if (Integer.toString(ir.hashCode()).equals(id))				
//					System.out.println(id + ": " + ir.getRelation().getName());
//			}
//		}
//		System.out.println("--------------------------------------------------------");		
		
		clean();
		getFilterDatabase().clean();
		
		Map<String,InstanceEntity> map = new HashMap<String,InstanceEntity>();
		System.out.println("Creating intermediate apply entities...");
		// not match entity filters though - those are created in the constructor
		if(!CreateApplyEntities(control,applyModel, applyMetaModel,map)) 
			return false;		
		System.out.println("Creating entities for matching...");
		if(!CreateMatchEntities(matchModel, matchMetaModel,map)) 
			return false;
		System.out.println("Creating relations for matching...");
		if(!CreateRelations(matchModel, matchMetaModel)) 
			return false;
		
		String orderedPositiveRelations = CreateOrderedRelations();
		float ratio = (_temporalCounter+1) / (_applyCounter+1);
		
		if(ratio < 5.0) {
			System.out.println("choosing type 1 heuristics:" + _temporalCounter +", " + _applyCounter + ", " + ratio);
			new Clause(getPositiveJoinHead()+"("+orderedPositiveRelations+getPositiveJoinBody()+")");
		} else {
			System.out.println("choosing type 2 heuristics:" + _temporalCounter +", " + _applyCounter + ", " + ratio);
			new Clause(getPositiveJoinHead2()+"("+orderedPositiveRelations+getPositiveJoinBody2()+")");
		}
		
		if(!getNegativeJoinPredicate().isEmpty()) 
			new Clause(getNegativeJoinPredicate());
		
		new Clause(getCutPredicate());
		
		new Clause(getQueryDifferentEntitiesFact());
		
		System.out.println("Query: " + getQuery());
		Query q = new Query(getQuery());
		
		// keeps track of when solutions are identified
		// firstRound refers to regular, non-lifted call
		// secondRound refers to the lifted version (treating negative matchers as positive)
		boolean firstRoundSolution = false, secondRoundSolution = false;
		
		// first, try the regular query
		if(q.hasMoreSolutions()) {
			do { 
				// do the check for the solutions here and eliminate the cut in the prolog query
				// each solution needs to be checked for satisfaction of the presence conditions
				Hashtable solution = q.nextSolution();
				//System.out.println(solution.toString());
				
				// this condition will either be empty or contain the presence conditions of 
				// all in-between elements in negative indirect links
				String negativeIndirectCondition = "";
				
				// only when negative matchers are treated as positive (and thus are present in the solution)
				if(treatNegativeAsPositive) {
					Enumeration matcherIds = solution.keys();
					// iterate over match elements; if any is the target of a negative indirect link
					// the contents that satisfied the negative indirect link are sought				
					while (matcherIds.hasMoreElements()) {
						String matcherId = (String)matcherIds.nextElement();
						String indirectPresCond = "";
						
						if(getNegativeIndirectIDs().containsKey(matcherId)) {
							// start from the target, not the source, because each element can be the target of at most one containment association
							// it can be the source of many though
							String sourceObjectId = (solution.get(getNegativeIndirectIDs().get(matcherId))).toString().substring(1);
							String targetObjectId = solution.get(matcherId).toString().substring(1);
							// the target object changes as each target element is found; each new target is at a lesser depth than the previous
							// until eventually the source element being searched for is found
							while(!sourceObjectId.equals(targetObjectId)) {
								for(InstanceRelation ir : matchModel.getLoadedRelations()) {
									if(ir.getRelation().isContainment() && Integer.toString(ir.getTarget().hashCode()).equals(targetObjectId)) {
										// along the way, the presence conditions of the in-between object and association are included
										targetObjectId = Integer.toString(ir.getSource().hashCode());
										indirectPresCond += presenceConds.get(Integer.toString(ir.hashCode())) + " ";
										
										if(!sourceObjectId.equals(targetObjectId)) {
											indirectPresCond += presenceConds.get(targetObjectId) + " ";
										}
										
										break;
									}
								}
							}
						}
						negativeIndirectCondition += indirectPresCond; 
					}
				}
				
				// either negativeCondition is set to an empty string or the presence conditions of 
				// all the in-between entities in all the indirect links
				String negativeCondition = negativeIndirectCondition;
				// remove objects matched to the negative matchers
				// conjoin all the objects' presence conditions together; this clause is what shouldn't be satisfied
				// keep track of this to add it to the corresponding solution presence condition
				for(String negativeID : getNegativeIDs()) {
					if(solution.containsKey((String)negativeID)) {
						String emfObjectID = (solution.get((String)negativeID)).toString();
						String objectPC = presenceConds.get(emfObjectID.substring(1));
						
						if(objectPC != null) {
							negativeCondition += (objectPC + " ");
						}
						
						solution.remove(negativeID);
					}
				}
				
				if(!negativeCondition.isEmpty()) 
					negativeCondition = "(not (and " + negativeCondition.substring(0, negativeCondition.length() - 1) + "))";
				
				for(String backwardLinkID : _backwardLinkIds) {
					solution.remove(backwardLinkID); 
				}
				
//				System.out.println(solution.toString());
//				Enumeration ids = solution.keys();
//				System.out.println("-------------------------------------");
//				System.out.println("Solutions and presence conditions:");				
//				while (ids.hasMoreElements()) {
//					String emfObjectID = (solution.get((String)ids.nextElement())).toString();
//					System.out.println(emfObjectID + ": " + presenceConds.get(emfObjectID.substring(1)));
//				}

				String presenceCondsFormula = buildZ3PresenceCondFormula(featureFormula, presenceConds, solution);

				// check for the applicability condition of this particular result for the rule's match
				Z3Solver z3solver = new Z3Solver();

//				System.out.println();
//				System.out.println("z3 Formula to check: " + presenceCondsFormula);			
				if (z3solver.checkSat(presenceCondsFormula) == Z3Bool.SAT) {
//					System.out.println("The formula is SAT, keeping solution");
					
					if(!solution.isEmpty()) {
						if(!getBindingList().contains(solution)) {
							getBindingList().add(solution);	
							_solutionsNegativeComponent.add(negativeCondition);
						} else {
							// at this point, solutions that previously were identical except for their NAC objects are now identical
							// the two clauses representing a set of objects that satisfy the NAC should now be merged 
							// we do this because the match site they affect is the same, thus they affect the same solution condition
							int index = getBindingList().indexOf(solution);
							String previousCondition = _solutionsNegativeComponent.get(index);
							_solutionsNegativeComponent.set(index, "(and " + previousCondition + " " + negativeCondition + ")");
						}
					}
					
				}
//				else
//					System.out.println("The formula is UNSAT, discarding solution");					
//				System.out.println("-------------------------------------");
			}
			while(q.hasMoreSolutions());
//			System.out.println("--------------------------------------------------------");
//			System.out.println("Binding List:\n" + getBindingList());
//			System.out.println("--------------------------------------------------------");
			
			// check for each of the match sites (independently of whether it involves any or exists match classes) if the and of all presence conditions is satisfied
			// for each of the matching sites, throw the match site away if it's presence conditions are UNSAT
			// for the remaining match sites, whenever they share the same elements for the any match classes, 
			// take the sets of presence conditions for the remaining existential matches and negate their conjunction (only for the rewrite part)			
			
			firstRoundSolution = true;
		}
		
		// modify the query to not skip over match sites that may have been excluded for satisfying NAC
		// run the modified query (this will add to the list of solutions and corresponding negative conditions)
		if(!getNegativeIDs().isEmpty() && !treatNegativeAsPositive) {	// can never be run more than twice (i.e. after treatNegativeAsPositive is set to true)
			treatNegativeAsPositive = true;
			buildJoinPredicates();	
			secondRoundSolution = this.process(control, matchModel, applyModel, matchMetaModel, applyMetaModel, tp, presenceConds, featureFormula);
		}
		
		return (firstRoundSolution || secondRoundSolution);
	}
	
	/*
	 * Not very pretty code to generate the Z3 string containing the formula ANDing all the presence conditions
	 * in the elements in the solution, ANDed with the formula for the complete feature diagram
	 */
	private String buildZ3PresenceCondFormula(String featureFormula,
											  Hashtable<String,String> presenceConds, Hashtable solution) {
		// first find and gather all the boolean feature variables (starting with "_")
		// put all the presence conditions formulas in one string
		String z3formula = "";
		ArrayList<String> presenceCondFormulas = new ArrayList<String>();
		
		String mergedFormulas = (new String(featureFormula)) + " ";
		Enumeration ids = solution.keys();
		while (ids.hasMoreElements()) {
			String emfObjectID = (solution.get((String)ids.nextElement())).toString();
			String objPresCond = presenceConds.get(emfObjectID.substring(1));
			
			if (objPresCond != null)
				presenceCondFormulas.add(objPresCond);
				mergedFormulas = mergedFormulas + objPresCond + " ";
		}

		presenceCondFormulas.add(featureFormula);
		
		mergedFormulas = mergedFormulas.replace('(', ' ');
		mergedFormulas = mergedFormulas.replace(')', ' ');
		mergedFormulas = mergedFormulas.replace(',', ' ');
		
		// now catch all the tokens that start by _ (variable name) and build the
		// declare-const declaration for variables used in the formula
		Set<String> vars = new HashSet<String>();
		Scanner scanner = new Scanner(mergedFormulas);
        while (scanner.hasNext()) {
        	String token = scanner.next();
        	if (token.charAt(0) == '_'  && !(vars.contains(token))) {
        		z3formula += "(declare-const " + token + " Bool) ";
        		vars.add(token);
        	}
        }
        
        // finally AND the feature formula and the presence formulas for all matched elements in the model
        // start by adding the number of ANDS corresponding to the number of formulas involved -1
        // (because the and operator is binary)
        
        z3formula += "(assert ";
        if (presenceCondFormulas.size() == 1)
        	z3formula += presenceCondFormulas.get(0);
        else {
        	for (int i=0;i<presenceCondFormulas.size();i++)
            	z3formula += "(and ";
        	for (int i=0;i<presenceCondFormulas.size();i++)
        		z3formula += presenceCondFormulas.get(i) + ") ";
        }
        z3formula += ")";
        
		return z3formula;
	}

	private boolean CreateRelations(InstanceDatabase matchModel,MetaModelDatabase matchMetaModel
	) throws InvalidLayerRequirement {
		for(MatchRelationFilter rf : getMatchRelationFilters()) {
			if ((get_explicitMatchMetaModel() != null) && (get_explicitMatchModel() != null))
				rf.process(get_explicitMatchModel(), get_explicitMatchMetaModel());
			else
				rf.process(matchModel, matchMetaModel);
			if(rf.getFilterDatabase().isEmpty() && rf.getAssociation() instanceof PositiveMatchAssociation)
				return false;
			getFilterDatabase().union(rf.getFilterDatabase());
		}
		return true;
	}	
	
	private boolean CreateMatchEntities(InstanceDatabase matchModel,
			MetaModelDatabase matchMetaModel,Map<String,InstanceEntity> map) 
					throws InvalidLayerRequirement {
			// first only process positives...
			_temporalCounter = 0;
			InstanceDatabase runningMatchModel = matchModel;
			MetaModelDatabase runningMetaModel = matchMetaModel;
			if ((get_explicitMatchMetaModel() != null) && (get_explicitMatchModel() != null)) {
				runningMatchModel = get_explicitMatchModel();
				runningMetaModel = get_explicitMatchMetaModel();
			}
				
			Map<String,String> mapString = new HashMap<String,String>();
			for(MatchEntityFilter ef : getMatchEntityFilters()) {
				ef.process(runningMatchModel, runningMetaModel);
				
				if(ef.getFilterDatabase().isEmpty() && isPositive(ef.getMatchClass()))
					return false;
				for(InstanceEntity ie: ef.getFilterDatabase().getLoadedClasses()) {
					final String id = "i"+Integer.toString(ie.hashCode());					
					if(!mapString.containsKey(id+ie.getDotNotation())) {
						String factString = entityFact + "(" + id + ", " + 
						ie.getDotNotation()+ ", " + 
						generateAttributeValues(ie,runningMatchModel,runningMetaModel) + ",[";
						for(InstanceEntity ieparent: ie.getParents())
						{
							factString += "i"+Integer.toString(ieparent.hashCode())+ ", ";
						}
						factString += "0],[";
						for(InstanceEntity iepast: ie.getTemporalChildren()) 
						{
							if(map.get("i"+Integer.toString(iepast.hashCode()))!=null) {
								factString += "i"+Integer.toString(iepast.hashCode())+ ", ";
								_temporalCounter++;
							}
						}
						factString += "0])";
						new Clause(factString);
						mapString.put(id+ie.getDotNotation(),factString);
					}
				}
				getFilterDatabase().union(ef.getFilterDatabase());
			}
		return true;
	}

	private String generateAttributeValues(InstanceEntity ie, 
			InstanceDatabase database,MetaModelDatabase metaModel) {
		
		String entry = "[";

		boolean first = true;
		for(InstanceAttribute ia : database.getAttributesByInstanceEntity(ie)) {
			if(ia.getValue()!=null) {
				if(!first)
					entry+=", ";
				first = false;
				entry += "('_" + ia.getMetaAttribute().getName() + "', '" + ia.getValue() +"')";
			}
		}			
		return entry+"]";
	}	

	private boolean CreateApplyEntities(TransformationController control, InstanceDatabase applyModel,
			MetaModelDatabase applyMetaModel,
			Map<String,InstanceEntity> map) throws ClassNotFoundException,
			NoSuchFieldException, IllegalAccessException,
			NoSuchMethodException, InvocationTargetException,
			InvalidLayerRequirement {

			_applyCounter = 0;
			Map<String,String> mapString = new HashMap<String,String>();
			
			for(ApplyEntityFilter ef: getApplyEntityFilters()) {
				Pair<InstanceDatabase,MetaModelDatabase> pair = 
					ef.resolveForeignApplyClasses(control,applyModel, applyMetaModel);
				ef.process(pair.fst, pair.snd);
	
				if(ef.getFilterDatabase().isEmpty())
					return false;
				for(InstanceEntity ie: ef.getFilterDatabase().getLoadedClasses()) {
					final String id = "i"+Integer.toString(ie.hashCode());
					if(!mapString.containsKey(id+ie.getDotNotation())) {
						String factString = entityFact + "(" + id + ", " + ie.getDotNotation() + 
						", " + generateAttributeValues(ie,pair.fst,pair.snd) + ",[0],[0])";						
						new Clause(factString);
						map.put(id, ie);
						mapString.put(id+ie.getDotNotation(),factString);
						_applyCounter++;
					}
				}
				getFilterDatabase().union(ef.getFilterDatabase());
			}
			return true;
	}

	private String CreateOrderedRelations() {
		Map<InstanceEntity,List<InstanceRelation>> ermap = new HashMap<InstanceEntity,List<InstanceRelation>>();
		for(InstanceRelation ir: getFilterDatabase().getLoadedRelations()) {
			if(!ermap.containsKey(ir.getSource())) {
				List<InstanceRelation> tempList = new LinkedList<InstanceRelation>();
				tempList.add(ir);
				ermap.put(ir.getSource(), tempList);
			} else {
				List<InstanceRelation> irList = ermap.get(ir.getSource());
				irList.add(ir);
			}
		}		
		String orderedPositiveRelations="";
		{
			Map<String,Integer> mapStringCounter = new HashMap<String,Integer>();

			for(InstanceEntity ie: ermap.keySet()) {
				final String sourceID = "i"+Integer.toString(ie.hashCode());
				String factString = relationFact + "(" + sourceID+",["; 
				
				for(InstanceRelation ir: ermap.get(ie)) {
					final String id = "i"+Integer.toString(ir.hashCode());
					final String targetID = "i"+Integer.toString(ir.getTarget().hashCode());
					factString += "('" + ir.getRelation().getName() + "'," + targetID + ","+ id + "),";
					if(mapStringCounter.containsKey(ir.getRelation().getName()))
					{
						Integer counter = mapStringCounter.get(ir.getRelation().getName());
						mapStringCounter.put(ir.getRelation().getName(),++counter);
					} else {
						mapStringCounter.put(ir.getRelation().getName(),1);
					}
				}
				factString += "('',0,0)])";
				new Clause(factString);
			}
			while(!mapStringCounter.isEmpty()) {
				// getminimum
				String minString = "";
				Integer minValue = Integer.MAX_VALUE;
				for(String relationName: mapStringCounter.keySet()) {
					Integer value = mapStringCounter.get(relationName);
					if(value < minValue) {
						minValue = value;
						minString = relationName;
					}
				}
				for(QueryRelation qr: getPositiveRelations()) {
					if(qr.getRelationName().equals(minString)) {
						orderedPositiveRelations += qr.getQuery()+", ";
					}
				}
				mapStringCounter.remove(minString);
			}
		}
		return orderedPositiveRelations;
	}
	
	public static boolean isPositive(MatchAssociation association) {
		return association instanceof PositiveMatchAssociation || association instanceof PositiveIndirectAssociation || association instanceof MatchMayBeSameRelation;
	}

	public static boolean isPositive(AbstractTemporalRelation association) {
		return association instanceof PositiveBackwardRestriction;
	}	
	
	public static boolean isAnyPositive(MatchClass mclass) {
		return mclass instanceof AnyMatchClass;
	}
	
	public static boolean isPositive(MatchClass matchClass) {
		return matchClass instanceof PositiveMatchClass;
	}
	
	private boolean isExistPositive(MatchClass target) {
		return target instanceof ExistsMatchClass;
	}
	
	private boolean isExistentialClass(MatchClass mc) {
		return mc instanceof dsltrans.impl.ExistsMatchClassImpl;
	}
	
	private boolean isNegativeClass(MatchClass mc) {
		return mc instanceof dsltrans.impl.NegativeMatchClassImpl;
	}
	
	private boolean isNegativeAssociation(MatchAssociation ma) {
		return ma instanceof dsltrans.impl.NegativeMatchAssociationImpl;
	}
		
	public boolean updateFilters(@SuppressWarnings("rawtypes") Hashtable binding,InstanceDatabase matchModel, InstanceDatabase applyModel)
	{		
		for(MatchEntityFilter ef : getMatchEntityFilters()) {
			if(isPositive(ef.getMatchClass())) {
				String hashCode = ((Atom)binding.get(ef.getId())).toString();
				hashCode = hashCode.substring(1,hashCode.length());
				ef.setCurrentByHashId(getFilterDatabase(),Integer.parseInt(hashCode));
//				System.out.println("solution entity: " + hashCode + " " + ef.getCurrentEntity().getDotNotation());
//				for(MatchAttributeFilter maf : ef.getFilterAttributes()) {
//					System.out.println("\twith attribute: " + maf.getCurrentAttribute().getMetaAttribute().getName() + " with value: " + (maf.getCurrentAttribute().getValue() == null? "null" : maf.getCurrentAttribute().getValue().toString()));						
//				}
//				for(InstanceAttribute ia: matchModel.getAttributesByInstanceEntity(ef.getCurrentEntity())) {
//					System.out.println("\twith attribute: " + ia.getMetaAttribute().getName() + " with value: " + (ia.getValue()== null? "null" : ia.getValue().toString()));
//				}
			}
		}
		for(ApplyEntityFilter ef : getApplyEntityFilters()) {
			if(!onlyNegativeTemporals(ef)) {
				String hashCode = ((Atom)binding.get(ef.getId())).toString();
				hashCode = hashCode.substring(1,hashCode.length());
				if(!ef.setCurrentByHashId(getFilterDatabase(),Integer.parseInt(hashCode)))
					return false;
//				System.out.println("solution entity: " + hashCode + " " + ef.getCurrentEntity().getDotNotation());
//				for(ApplyAttributeFilter aaf : ef.getFilterAttributes()) {
//					System.out.println("\twith attribute: " + aaf.getCurrentAttribute().getMetaAttribute().getName() + " with value: " + (aaf.getCurrentAttribute().getValue() == null? "null" : aaf.getCurrentAttribute().getValue().toString()));
//				}
//				for(InstanceAttribute ia: applyModel.getAttributesByInstanceEntity(ef.getCurrentEntity())) {
//					System.out.println("\twith attribute: " + ia.getMetaAttribute().getName() + " with value: " + (ia.getValue()== null? "null" : ia.getValue().toString()));
//				}
			}
		}
		for(MatchRelationFilter rf : getMatchRelationFilters()) {
			if(!isIndirect(rf.getAssociation())) { // lets ignore indirect ones
				if(isPositive(rf.getAssociation())) {
					String hashCode = ((Atom)binding.get(rf.getId())).toString();
					hashCode = hashCode.substring(1,hashCode.length());
//					System.out.println("solution relation: " + hashCode);
					rf.setCurrentByHashId(getFilterDatabase(),Integer.parseInt(hashCode));
				}
			}
		}
		
		return true;
	}
	
	private boolean onlyNegativeTemporals(ApplyEntityFilter ef) {
		{  // temporal relations
			Iterator<TemporalRelationFilter> iter = getTemporalFilters().iterator();
			while(iter.hasNext()) {
				TemporalRelationFilter rf = iter.next();
				if(rf.getAssociation() instanceof PositiveBackwardRestriction) {
					String targetId = getIdFrom(rf.getTargetClass());
					if(ef.getId().equals(targetId))
						return false;
				}
			};
		}

		return true;
	}

	private boolean isIndirect(MatchAssociation association) {
		return association instanceof PositiveIndirectAssociation ||
		association instanceof NegativeIndirectAssociation;
	}
	
	private boolean isPositiveIndirect(MatchAssociation association) {
		return association instanceof PositiveIndirectAssociation;
	}
	
	private boolean isNegativeIndirect(MatchAssociation association) {
		return association instanceof NegativeIndirectAssociation;
	}
	
	public void buildJoinPredicates() {
		buildJoinPredicates1();
		buildJoinPredicates2();		
	}
	
	private void buildJoinPredicates1() {
		String queryBody = "";
		boolean first = true;			
		
		List<String> idList = new LinkedList<String>();
		
		// Keeps the id's from positive match and apply entities.
		List<String> positiveMatchAndApplyEntitiesIDList = new LinkedList<String>();			
		
		{ // regular match entities
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
				if(!exist(idList,ef.getId()))
					idList.add(ef.getId());
				
				// Collects all id's the represent positive match classes.  
				if (isPositive(ef.getMatchClass()) && !exist(positiveMatchAndApplyEntitiesIDList,ef.getId())) {
					assert !(ef.getMatchClass() instanceof ApplyClass);
					positiveMatchAndApplyEntitiesIDList.add(ef.getId());
				}
			};
		}
		
		{ // referenced apply entities from backward restrictions
			Iterator<ApplyEntityFilter> iter = getApplyEntityFilters().iterator();
			while(iter.hasNext()) {
				ApplyEntityFilter ef = iter.next();
					if(!exist(idList,ef.getId()))
						idList.add(ef.getId());
					
				// Collects all id's the represent apply classes.
				if (!exist(positiveMatchAndApplyEntitiesIDList,ef.getId())) {
					assert ef.getApplyClass() instanceof ApplyClass;
					positiveMatchAndApplyEntitiesIDList.add(ef.getId());
				}
			};
		}		
		{ // regular match associations
			Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
			while(iter.hasNext()) {
				MatchRelationFilter rf = iter.next();
				
				if(!isIndirect(rf.getAssociation())) {
					if(!exist(idList,rf.getId()))
						idList.add(rf.getId());					
				}
				if(isPositive(rf.getAssociation())
						&& !isExistPositive(rf.getAssociation().getSource()) // also exclude
						&& !isExistPositive(rf.getAssociation().getTarget()) // associations to exist elements
				) { // deal with positives first

					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					if(rf.getAssociation() instanceof PositiveMatchAssociation) {
						
						String query = relationFact+"("+sourceId+",RefList"+rf.getId()+"),"+
									" member(('"+rf.getAssociation().getAssociationName()+"',"+targetId+", "+rf.getId()+"), RefList"+rf.getId()+")";
						getPositiveRelations().add(new QueryRelation(rf.getAssociation().getAssociationName(),query));
					}
				}
			};
		}		

		{ // regular match entities
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
					if(isAnyPositive(ef.getMatchClass())) {// deal with any positives first
						if(!first)
							queryBody += ",";
						first = false;						
						queryBody += entityFact+"("+ef.getId()+","+ef.getDotNotation()+
						", AttributeList"+ef.getId() + 
						", "+"ParentList"+ef.getId()+",TemporalChildrenList"+ef.getId()+")";
												
						for(MatchAttributeFilter maf: ef.getFilterAttributes())
							if(maf.isAtomValue()) {
								String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
								queryBody += ", memberchk( "+pair+", AttributeList"+ef.getId()+")";
							}
					}
			};
		}
		
		{  // temporal relations
			Iterator<TemporalRelationFilter> iter = getTemporalFilters().iterator();
			while(iter.hasNext()) {
				TemporalRelationFilter rf = iter.next();
				if(isPositive(rf.getAssociation())
						&& !isExistPositive(rf.getSourceClass()) // also exclude
				) {
					if(!exist(idList,rf.getId()))
						idList.add(rf.getId());	
					if(!first)
						queryBody += ",";
					first = false;		
					
					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					queryBody += "member("+targetId+", TemporalChildrenList"+sourceId+")";
				}
			};
		}		
		
		{ // referenced apply entities from backward restrictions
			Iterator<ApplyEntityFilter> iter = getApplyEntityFilters().iterator();
			while(iter.hasNext()) {
				ApplyEntityFilter ef = iter.next();
				
				if(checkTemporalNoCut(ef)) {
					if(!first)
						queryBody += ",";
					first = false;				
					queryBody += entityFact+"("+ef.getId()+","+ef.getDotNotation()+
					", AttributeList"+ef.getId() + 
					",[0],[0])";
																
					for(ApplyAttributeFilter maf: ef.getFilterAttributes())
						if(maf.isAtomValue()) {
							String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
							queryBody += ", memberchk( "+pair+", AttributeList"+ef.getId()+")";
						}
				}
			};
		}
		
		{ // indirect match associations
			Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
			while(iter.hasNext()) {
				MatchRelationFilter rf = iter.next();
				
				if((isPositive(rf.getAssociation()))
						&& !isExistPositive(rf.getAssociation().getSource()) // also exclude
						&& !isExistPositive(rf.getAssociation().getTarget()) // associations to exist elements
				) { // deal with positives first

					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					
					if(rf.getAssociation() instanceof PositiveIndirectAssociation) {
						if(!first)
							queryBody += ",";
						first = false;						
//queryBody += neighbourFact+"("+rf.getId()+","+ sourceId +","+ targetId +")";
						queryBody += "memberchk("+sourceId+", ParentList"+targetId+")";
					}
				}
			};
		}
		
		
		
		{
			// Check for attribute relations here.
			for (AbstractAttributeRelation aatr: getOwnAttributeRelations()) {
				// Catch the relevant filters and add predicates to query body.
				if(!first){
					queryBody += ",";
				}
				first = false;
				queryBody += getPredicatesFromAttributeRelation(aatr);
			}
			
		}
		
		setQueryHead(compileHead("", idList));
		
		_query = "queryjoin("+ getQueryHead() + ").";
		
		String cutclause = getCutClause();
		if(!cutclause.isEmpty()){
			queryBody += "," + cutclause;
			first = false;
		}	
		
		
		generateDifferentEntitiesFactCall(positiveMatchAndApplyEntitiesIDList);
		
		String negativeclause = getNegativeClauses(getQueryHead(),true);
		if(!negativeclause.isEmpty()) {
			// query is modified here to treat negative matchers as positive when option is set
			queryBody += (treatNegativeAsPositive) ? ("," + negativeclause) : (",not(" + negativeclause + ")");
			first = false;
		}
		
		{
			// Assure different id's match different entities.
			if (!getQueryDifferentEntitiesHead().isEmpty()) {
				if(!first){
					queryBody += ",";
				}
				first = false;
				queryBody += getQueryDifferentEntitiesHead();
			}
			
		}
		
		if(queryBody.matches(",.*"))
			queryBody = queryBody.substring(1);
		
		System.out.println("positiveJoinPredicate: queryjoin("+ getQueryHead()+") :- \n" + queryBody);
		_positiveJoinHead = "queryjoin("+ getQueryHead()+") :- ";
		_positiveJoinBody = queryBody;
		
	}

	private String getPredicatesFromAttributeRelation(
			AbstractAttributeRelation aatr) {
		
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
			assert source_aaf != null;
		}
		assert source_maf!=null || source_aaf != null;
		
		target_maf = findMatchAttributeFilter(target);
		if (target_maf==null) {
			// The attribute relation doesn't start in the match model.
			// It must be in the apply model. Or else it's an error...
			target_aaf = findApplyAttributeFilter(target);
			assert target_aaf != null;
		}
		assert target_maf!=null || target_aaf != null;
		
		// Exactly one of the following combinations will hold.
		assert (source_maf!=null && target_maf!=null) 
				|| (source_maf!=null && target_aaf!=null)
				|| (source_aaf!=null && target_maf!=null)
				|| (source_aaf!=null && target_aaf!=null);
		
		if (source_maf!=null && target_maf!=null) {
			String pair1 = "('_"+source_maf.getName()+"', Attr"+source_maf.getId()+")";
			String pair2 = "('_"+target_maf.getName()+"', Attr"+target_maf.getId()+")";
			String res = "memberchk( "+ pair1 + ", AttributeList"+source_maf.getEntity().getId()+")";
			res += ", " + "memberchk( "+ pair2 + ", AttributeList"+target_maf.getEntity().getId()+")";
			
			// One of these must hold.
			if (aatr instanceof AttributeEqualityImpl) {
				res += ", Attr" + source_maf.getId() + " == Attr" + target_maf.getId();
			}
			if (aatr instanceof AttributeInequalityImpl) {
				res += ", Attr" + source_maf.getId() + " \\== Attr" + target_maf.getId();
			}
			
			return res;
		}
		if (source_maf!=null && target_aaf!=null) {
			String pair1 = "('_"+source_maf.getName()+"', Attr"+source_maf.getId()+")";
			String pair2 = "('_"+target_aaf.getName()+"', Attr"+target_aaf.getId()+")";
			String res = "memberchk( "+ pair1 + ", AttributeList"+source_maf.getEntity().getId()+")";
			res += ", " + "memberchk( "+ pair2 + ", AttributeList"+target_aaf.getEntity().getId()+")";
			
			// One of these must hold.
			if (aatr instanceof AttributeEqualityImpl) {
				res += ", " + source_maf.getId() + " == Attr" + target_aaf.getId();
			}
			if (aatr instanceof AttributeInequalityImpl) {
				res += ", " + source_maf.getId() + " \\== Attr" + target_aaf.getId();
			}
			return res;
		}
		if (source_aaf!=null && target_maf!=null) {
			String pair1 = "('_"+source_aaf.getName()+"', Attr"+source_aaf.getId()+")";
			String pair2 = "('_"+target_maf.getName()+"', Attr"+target_maf.getId()+")";
			String res = "memberchk( "+ pair1 + ", AttributeList"+source_aaf.getEntity().getId()+")";
			res += ", " + "memberchk( "+ pair2 + ", AttributeList"+target_maf.getEntity().getId()+")";
			
			// One of these must hold.
			if (aatr instanceof AttributeEqualityImpl) {
				res += ", " + source_aaf.getId() + " == Attr" + target_maf.getId();
			}
			if (aatr instanceof AttributeInequalityImpl) {
				res += ", " + source_aaf.getId() + " \\== Attr" + target_maf.getId();
			}
			return res;
		}
		if (source_aaf!=null && target_aaf!=null) {
			String pair1 = "('_"+source_aaf.getName()+"', Attr"+source_aaf.getId()+")";
			String pair2 = "('_"+target_aaf.getName()+"', Attr"+target_aaf.getId()+")";
			String res = "memberchk( "+ pair1 + ", AttributeList"+source_aaf.getEntity().getId()+")";
			res += ", " + "memberchk( "+ pair2 + ", AttributeList"+target_aaf.getEntity().getId()+")";
			
			// One of these must hold.
			if (aatr instanceof AttributeEqualityImpl) {
				res += ", " + source_aaf.getId() + " == Attr" + target_aaf.getId();
			}
			if (aatr instanceof AttributeInequalityImpl) {
				res += ", " + source_aaf.getId() + " \\== Attr" + target_aaf.getId();
			}
			return res;
		}
		
		assert (false);
		
		return "";
	}

	private List<AbstractAttributeRelation> getOwnAttributeRelations() {
		List<AbstractAttributeRelation> res = new LinkedList<AbstractAttributeRelation>();
		for (AbstractAttributeRelation aatr: _transformationRule.getRule().getAttributeRelations()) {
			if (containedAbstractAttributeRelation(aatr)) {
				res.add(aatr);
			}
		}
		return res;
	}

	private boolean containedAbstractAttributeRelation(
			AbstractAttributeRelation aatr) {
		Attribute source = aatr.getSourceAttribute();
		Attribute target = aatr.getTargetAttribute();
		
		MatchAttributeFilter source_maf = null;
		ApplyAttributeFilter source_aaf = null;
		MatchAttributeFilter target_maf = null;
		ApplyAttributeFilter target_aaf = null;
		
		source_maf = findMatchAttributeFilter(source);
		if (source_maf==null) {
			// The attribute relation doesn't start in the match model.
			// Let's try the apply model
			source_aaf = findApplyAttributeFilter(source);
		}
		target_maf = findMatchAttributeFilter(target);
		if (target_maf==null) {
			// The attribute relation doesn't start in the match model.
			// Let's try the apply model
			target_aaf = findApplyAttributeFilter(target);
		}
		
		return (source_maf!=null || source_aaf!=null) && (target_maf !=null || target_aaf!=null) ;
	}

	private void generateDifferentEntitiesFactCall(List<String> idList) {
		setQueryDifferentEntitiesHead("");
		setQueryDifferentEntitiesFact("");
		
		Set<String> usedIds = new HashSet<String>();
		
		String queryBody = "";
		assert queryBody.isEmpty();
		
		if(!idList.containsAll(_negativeClassIds))
			idList.addAll(_negativeClassIds);
		
		boolean first = true;
		for (int i=0; i<idList.size(); i++) {
			for (int j=i+1; j<idList.size(); j++) {
				String idi = idList.get(i);
				String idj = idList.get(j);
				
				if (!mayBeSameRelations(idi, idj)) {
					if (!first) {
						queryBody+=",";
					}
					first = false;
				
					queryBody += idi + " \\== " + idj;
					usedIds.add(idi);
					usedIds.add(idj);
				}
			}
		}
		
		assert queryBody != null;
		
		if (!queryBody.isEmpty()) {
			
			queryBody = "(" + queryBody + ")";
			
			setQueryDifferentEntitiesHead(DIFF_ATTR_FACT + "(" + compileHead("", convertSetToList(usedIds)) + ")");
			
			setQueryDifferentEntitiesFact(getQueryDifferentEntitiesHead() + " :- " + queryBody);
			
		}
	}


//	private boolean matchingSameClass(MatchEntityFilter fixedElement,
//			MatchEntityFilter element) {
//		return fixedElement.getDotNotation().equals(element.getDotNotation());
//	}

	private List<String> convertSetToList(Set<String> usedIds) {
		
		List<String> res = new LinkedList<String>();
		
		for (String el : usedIds) {
			res.add(el);
		}
		
		return res;
	}

	private boolean mayBeSameRelations(String idi, String idj) {
		assert !idi.equals(idj);
		
		/*
		 * 
		 * 	get corresponding entities
		 * 	if entities are in the same match or apply model {
		 *   return true if there is a maybesame relation between them.
		 *   false otherwise
		 */
		
		MatchClass mc_i = findMatchClass(idi);
		MatchClass mc_j = findMatchClass(idj);
		
		if (mc_i != null && mc_j != null) {
			return _transformationRule.hasMayBeSameRelation(mc_i, mc_j);
		}
		
		ApplyClass ac_i = findApplyClass(idi);
		ApplyClass ac_j = findApplyClass(idj);
		
		if (ac_i != null && ac_j != null) {
			return _transformationRule.hasMayBeSameRelation(ac_i, ac_j);
		}
		
		return false;
	}

	private ApplyClass findApplyClass(String idi) {
		for(ApplyEntityFilter ef : getApplyEntityFilters()) {
			if (ef.getId().equals(idi)) {
				return ef.getApplyClass();
			}
		}
		return null;
	}

	private MatchClass findMatchClass(String idi) {
		for(MatchEntityFilter ef : getMatchEntityFilters()) {
			if (ef.getId().equals(idi)) {
				return ef.getMatchClass();
			}
		}
		return null;
	}

	private void buildJoinPredicates2() {
		String queryBody = "";
		boolean first = true;			
		
		List<String> idList = new LinkedList<String>();			
		
		// Keeps the id's from positive match and apply entities.
		List<String> positiveMatchAndApplyEntitiesIDList = new LinkedList<String>();			
				
		
		{ // regular match entities
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
					if(!exist(idList,ef.getId()))
						idList.add(ef.getId());
				
				// Collects all id's the represent positive match classes. 
				if (isPositive(ef.getMatchClass()) && !exist(positiveMatchAndApplyEntitiesIDList,ef.getId())) {
					assert !(ef.getMatchClass() instanceof ApplyClass);
					positiveMatchAndApplyEntitiesIDList.add(ef.getId());
				}
			};
		}
		
		{ // referenced apply entities from backward restrictions
			Iterator<ApplyEntityFilter> iter = getApplyEntityFilters().iterator();
			while(iter.hasNext()) {
				ApplyEntityFilter ef = iter.next();
					if(!exist(idList,ef.getId()))
						idList.add(ef.getId());
				
				// Collects all id's the represent apply classes.
				if (!exist(positiveMatchAndApplyEntitiesIDList,ef.getId())) {
					assert ef.getApplyClass() instanceof ApplyClass;
					positiveMatchAndApplyEntitiesIDList.add(ef.getId());
				}
			};
		}		
		{ // regular match associations
			Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
			while(iter.hasNext()) {
				MatchRelationFilter rf = iter.next();
				
				if(!isIndirect(rf.getAssociation())) {
					if(!exist(idList,rf.getId()))
						idList.add(rf.getId());					
				}
//				if(isPositive(rf.getAssociation())
//						&& !isExistPositive(rf.getAssociation().getSource()) // also exclude
//						&& !isExistPositive(rf.getAssociation().getTarget()) // associations to exist elements
//				) { // deal with positives first
//
//					String sourceId = getIdFrom(rf.getSourceClass());
//					String targetId = getIdFrom(rf.getTargetClass());
//					if(rf.getAssociation() instanceof PositiveMatchAssociation) {
//						
//						String query = relationFact+"("+sourceId+",RefList"+rf.getId()+"),"+
//									" member(('"+rf.getAssociation().getAssociationName()+"',"+targetId+", "+rf.getId()+"), RefList"+rf.getId()+")";
//						getPositiveRelations().add(new QueryRelation(rf.getAssociation().getAssociationName(),query));
//					}
//				}
			};
		}		

		{ // regular match entities
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
					if(isAnyPositive(ef.getMatchClass())) {// deal with any positives first
						if(!first)
							queryBody += ",";
						first = false;						
						queryBody += entityFact+"("+ef.getId()+","+ef.getDotNotation()+
						", AttributeList"+ef.getId() + 
						", "+"ParentList"+ef.getId()+",TemporalChildrenList"+ef.getId()+")";
																							
						for(MatchAttributeFilter maf: ef.getFilterAttributes())
							if(maf.isAtomValue()) {
								String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
								queryBody += ", memberchk( "+pair+", AttributeList"+ef.getId()+")";
							}						
						
					}
			};
		}
		
		{ // referenced apply entities from backward restrictions
			Iterator<ApplyEntityFilter> iter = getApplyEntityFilters().iterator();
			while(iter.hasNext()) {
				ApplyEntityFilter ef = iter.next();
				
				if(checkTemporalNoCut(ef)) {
					if(!first)
						queryBody += ",";
					first = false;				
					queryBody += entityFact+"("+ef.getId()+","+ef.getDotNotation()+
					", AttributeList"+ef.getId() + 
					",[0],[0])";
																			
					for(ApplyAttributeFilter maf: ef.getFilterAttributes())
						if(maf.isAtomValue()) {
							String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
							queryBody += ", memberchk( "+pair+", AttributeList"+ef.getId()+")";
						}						

				}
			};
		}
		
		{ // indirect match associations
			Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
			while(iter.hasNext()) {
				MatchRelationFilter rf = iter.next();
				
				if((isPositive(rf.getAssociation())
						&& !isExistPositive(rf.getAssociation().getSource()) // also exclude
						&& !isExistPositive(rf.getAssociation().getTarget()) // associations to exist elements
				)) { // deal with positives first

					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					
					if(rf.getAssociation() instanceof PositiveIndirectAssociation) {
						if(!first)
							queryBody += ",";
						first = false;						
//queryBody += neighbourFact+"("+rf.getId()+","+ sourceId +","+ targetId +")";
						queryBody += "memberchk("+sourceId+", ParentList"+targetId+")";
					}
				}
			};
		}		
		
		{  // temporal relations
			Iterator<TemporalRelationFilter> iter = getTemporalFilters().iterator();
			while(iter.hasNext()) {
				TemporalRelationFilter rf = iter.next();
				if(isPositive(rf.getAssociation())
						&& !isExistPositive(rf.getSourceClass()) // also exclude
				) {
					if(!exist(idList,rf.getId()))
						idList.add(rf.getId());	
					if(!first)
						queryBody += ",";
					first = false;		
					
					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					queryBody += "memberchk("+targetId+", TemporalChildrenList"+sourceId+")";
				}
			};
		}		
		
		
		
		{
			// Check for attribute relations here.
			for (AbstractAttributeRelation aatr: getOwnAttributeRelations()) {
				// Catch the relevant filters and add predicates to query body.
				if(!first){
					queryBody += ",";
				}
				first = false;
				queryBody += getPredicatesFromAttributeRelation(aatr);
			}
			
		}
		
		setQueryHead(compileHead("", idList));
		
		_query = "queryjoin("+ getQueryHead() + ").";
		
		String cutclause = getCutClause();
		if(!cutclause.isEmpty()){
			queryBody += "," + cutclause;
		}
		

		generateDifferentEntitiesFactCall(positiveMatchAndApplyEntitiesIDList);
		
		String negativeclause = getNegativeClauses(getQueryHead(),false);
		if(!negativeclause.isEmpty()) {
			// query is modified here to treat negative matchers as positive when option is set
			queryBody += (treatNegativeAsPositive) ? ("," + negativeclause) : (",not(" + negativeclause + ")");
		} 
		
		{
			// Assure different id's match different entities.
			if (!getQueryDifferentEntitiesHead().isEmpty()) {
				if(!first){
					queryBody += ",";
				}
				first = false;
				queryBody += getQueryDifferentEntitiesHead();
			}
			
		}
		
		if(queryBody.matches(",.*"))
			queryBody = queryBody.substring(1);
		
		System.out.println("positiveJoinPredicate2: queryjoin("+ getQueryHead()+") :- \n" + queryBody);
		_positiveJoinHead2 = "queryjoin("+ getQueryHead()+") :- ";
		_positiveJoinBody2 = queryBody;
	}

	private boolean checkTemporalNoCut(ApplyEntityFilter ef) {
		boolean cancontinue = false;
		{Iterator<TemporalRelationFilter> iter = getTemporalFilters().iterator();
		while(iter.hasNext()) {
			TemporalRelationFilter rf = iter.next();
			if(isPositive(rf.getAssociation())
					&& !isExistPositive(rf.getSourceClass())
					&& rf.getTargetClass() == ef.getApplyClass()) // also exclude
					{
						cancontinue = true;
					}
		}}
		return cancontinue;
	}

	private String compileHead(String queryHead, List<String> idList) {
		boolean first = true;
		for(String id: idList) {
			if(!first)
				queryHead += ",";
			queryHead += id;
			first = false;

		}
		return queryHead;
	}
	
	private String getIdFrom(ApplyClass targetClass) {
		for(ApplyEntityFilter ef : getApplyEntityFilters()) {
			if(ef.getApplyClass() == targetClass)
				return ef.getId();
		}
		return "";
	}

	private String getCutClause() {
		String queryBody = "";
		String queryClause = "";
		if(!hasExist()) return "";
		boolean first = true;
		
		List<String> idList = new LinkedList<String>();		
		{
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
				if(isExistPositive(ef.getMatchClass())) {// deal with exist
					if(!exist(idList,ef.getId()))
						idList.add(ef.getId());
				}
			};			
		}{ // regular match associations
			Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
			while(iter.hasNext()) {
				MatchRelationFilter rf = iter.next();
				if(isPositive(rf.getAssociation())
						&& (isExistPositive(rf.getAssociation().getSource())
						||  isExistPositive(rf.getAssociation().getTarget()))
				) { // deal with positives first
					
					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					if(!exist(idList,sourceId))
						idList.add(sourceId);
					if(!exist(idList,targetId))
						idList.add(targetId);
					
					if(rf.getAssociation() instanceof PositiveMatchAssociation) {
						if(!first)
							queryBody += ",";
						first = false;						
//queryBody += relationFact+"("+sourceId+"," + "'" + rf.getAssociation().getAssociationName() + "'," + rf.getId() +","+ targetId +")";
						queryBody += relationFact+"("+sourceId+",RefList"+rf.getId()+"),"+
						" member(('"+rf.getAssociation().getAssociationName()+"',"+targetId+", "+rf.getId()+"), RefList"+rf.getId()+")";
						if(!exist(idList,rf.getId()))
							idList.add(rf.getId());
					}
				}
			};
		}{
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
				if(isExistPositive(ef.getMatchClass())) {// deal with exist
					if(!first)
						queryBody += ",";
					queryBody += entityFact+"("+ef.getId()+","+ef.getDotNotation()+
					", AttributeList"+ef.getId() + 
					", ParentList"+ef.getId()+",TemporalChildrenList"+ef.getId()+")";
					
					for(MatchAttributeFilter maf: ef.getFilterAttributes())
						if(maf.isAtomValue()) {
							String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
							queryBody += ", memberchk( "+pair+", AttributeList"+ef.getId()+")";
						}
					first = false;
				}
			}
		};
		
		{  // temporal relations
			Iterator<TemporalRelationFilter> iter = getTemporalFilters().iterator();
			while(iter.hasNext()) {
				TemporalRelationFilter rf = iter.next();
				if(isPositive(rf.getAssociation()) && isExistPositive(rf.getAssociation().getSourceClass())
				) {
					if(!exist(idList,rf.getId()))
						idList.add(rf.getId());	
					if(!first)
						queryBody += ",";
					first = false;					

					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					queryBody += "member("+targetId+", TemporalChildrenList"+sourceId+")";
				}
			};
		}		

		{ // referenced apply entities from backward restrictions
			Iterator<ApplyEntityFilter> iter = getApplyEntityFilters().iterator();
			while(iter.hasNext()) {
				ApplyEntityFilter ef = iter.next();
				
				if(!checkTemporalNoCut(ef)) {
					if(!exist(idList,ef.getId()))
						idList.add(ef.getId());						
					if(!first)
						queryBody += ",";
					first = false;				
					queryBody += entityFact+"("+ef.getId()+","+ef.getDotNotation()+
					", AttributeList"+ef.getId() + 
					",[0],[0])";
					
					for(ApplyAttributeFilter maf: ef.getFilterAttributes())
						if(maf.isAtomValue()) {
							String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
							queryBody += ", memberchk( "+pair+", AttributeList"+ef.getId()+")";
						}											
				}
			};
		}		
		
		{ // indirect match associations
			Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
			while(iter.hasNext()) {
				MatchRelationFilter rf = iter.next();
				
				if((isPositive(rf.getAssociation())
						&& (isExistPositive(rf.getAssociation().getSource())
						||  isExistPositive(rf.getAssociation().getTarget()))
				)) { // deal with positives first
					
					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					if(!exist(idList,sourceId))
						idList.add(sourceId);
					if(!exist(idList,targetId))
						idList.add(targetId);
					
					if(rf.getAssociation() instanceof PositiveIndirectAssociation) {
						if(!first)
							queryBody += ",";
						first = false;						
//queryBody += neighbourFact+"("+rf.getId()+","+ sourceId +","+ targetId +")";
						queryBody += "memberchk("+sourceId+", ParentList"+targetId+")";
					}
				}
			};
		}		
		
		String cutHead = compileHead("", idList);
		setQueryCutHead(cutHead);
		queryClause = "cutClause("+cutHead +")";
		cutHead = queryClause + " :- ";
		// System.out.println("cutPredicate: " + cutHead + "("+queryBody+",!)");
		// lifted version of DSLTrans without the cut
		//_cutPredicate  = cutHead + "("+queryBody+",!)";
		System.out.println("cutPredicate: " + cutHead + "("+queryBody+")");
		_cutPredicate  = cutHead + "("+queryBody+")";
		
		return queryClause;
	}

	private boolean exist(List<String> idList, String elemId) {
		for(String id: idList) {
			if(id.equals(elemId))
				return true;
		}
		return false;
	}

	private String getNegativeClauses(String positivequeryHead, boolean addToNegativeJoinPredicates) {
		List<String> queryBodyList = new LinkedList<String>();
		String queryClause = "";
		if(!hasNegatives()) return "";

		getQueryBodyByEntity(queryBodyList);
		getQueryBodyByRelation(queryBodyList);
		queryClause = "nqueryjoin(" + positivequeryHead + ")";
		// when running the query for the second time (i.e. when treatNegativeAsPositive is true), stop here
		// if not, same elements in _negativeJoinPredicates added again
		if(!addToNegativeJoinPredicates) return queryClause;
		String queryHead = "nqueryjoin(" + positivequeryHead + ") :- ";
		
		boolean first = true;
		String queryBody = "";
		for(String body: queryBodyList) {
			System.out.println(body);
			queryBody += ((first) ? "" : ",") + body;
			first = false;
		}

		// if treatNegativeAsPoitive
		if(!getQueryDifferentEntitiesHead().isEmpty()) queryBody += "," + getQueryDifferentEntitiesHead();
		setNegativeJoinPredicate(queryHead + "("+queryBody+")");
		System.out.println("negativeJoinPredicates: " + queryHead + "("+queryBody+")");
		
		//else
//		for(String queryBody: queryBodyList) {
//			_negativeJoinPredicates.add(queryHead + "("+queryBody+")");
//			//added by Kawin
//			if(!getQueryDifferentEntitiesHead().isEmpty()) queryBody += "," + getQueryDifferentEntitiesHead();
//			System.out.println("\n\nnegativeJoinPredicates: " + queryHead + "("+queryBody+")\n\n");			
//		}
		
		return queryClause;
	}

	private void getQueryBodyByRelation(List<String> queryBodyList) {
		{
			Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
			while(iter.hasNext()) {
				MatchRelationFilter rf = iter.next();
				
				if(rf.getAssociation() instanceof NegativeMatchAssociation
						&&
						isPositive(rf.getSourceClass()) &&
						isPositive(rf.getTargetClass())		
				) {
					String relationId = rf.getId();
					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());
					String queryBody1 = relationFact+"("+sourceId+",RefList"+relationId+"),"+
								" member(('"+rf.getAssociation().getAssociationName()+"',"+targetId+", "+relationId+"), RefList"+relationId+"),";
					String sourceDotNotation = "'"+rf.getSourceClass().getPackageName()+"."+rf.getSourceClass().getClassName()+"'";
					String targetDotNotation = "'"+rf.getTargetClass().getPackageName()+"."+rf.getTargetClass().getClassName()+"'";
					
					queryBody1 += entityFact+"("+sourceId+","+sourceDotNotation+",AttributeList"+sourceId+",_,_),";
					queryBody1 += entityFact+"("+targetId+","+targetDotNotation+",AttributeList"+targetId+",_,_)";
					queryBodyList.add(queryBody1);							
					
				} else if(rf.getAssociation() instanceof NegativeIndirectAssociation
						&&
						isPositive(rf.getSourceClass()) &&
						isPositive(rf.getTargetClass())
						) { 
					String sourceId = getIdFrom(rf.getSourceClass());
							String targetId = getIdFrom(rf.getTargetClass());
					String queryBody1 = "";
					String sourceDotNotation = "'"+rf.getSourceClass().getPackageName()+"."+rf.getSourceClass().getClassName()+"'";
					String targetDotNotation = "'"+rf.getTargetClass().getPackageName()+"."+rf.getTargetClass().getClassName()+"'";
					queryBody1 += entityFact+"("+sourceId+","+sourceDotNotation+",AttributeList"+sourceId+","+"ParentList"+sourceId+",_),";
					queryBody1 += entityFact+"("+targetId+","+targetDotNotation+",AttributeList"+targetId+","+"ParentList"+targetId+",_),";
					queryBody1 += "memberchk("+ sourceId +", ParentList"+targetId +")";
					queryBodyList.add(queryBody1);					
				}
			};
		}
		{  // temporal relations
			Iterator<TemporalRelationFilter> iter = getTemporalFilters().iterator();
			while(iter.hasNext()) {
				TemporalRelationFilter rf = iter.next();
				if(rf.getAssociation() instanceof NegativeBackwardRestriction
						&&
						isPositive(rf.getSourceClass())		
				) {
					String sourceId = getIdFrom(rf.getSourceClass());
					String targetId = getIdFrom(rf.getTargetClass());										
					String sourceDotNotation = "'"+rf.getSourceClass().getPackageName()+"."+rf.getSourceClass().getClassName()+"'";
					String queryBody = entityFact+"("+sourceId+","+sourceDotNotation+",_,_,TemporalChildrenList"+sourceId+")";
					{ // referenced apply entities from backward restrictions
						Iterator<ApplyEntityFilter> appiter = getApplyEntityFilters().iterator();
						while(appiter.hasNext()) {
							ApplyEntityFilter ef = appiter.next();
							if(ef.getId().equals(targetId)) {
								queryBody += ", "+entityFact+"("+ef.getId()+","+ef.getDotNotation()+
								", AttributeList"+ef.getId() + 
								",[0],[0])";
								
								for(ApplyAttributeFilter maf: ef.getFilterAttributes())
									if(maf.isAtomValue()) {
										String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
										queryBody += ", memberchk( "+pair+", AttributeList"+ef.getId()+")";
									}
								break;
							}
						};
					}
					queryBody += ", member("+targetId+", TemporalChildrenList"+sourceId+")";
					queryBodyList.add(queryBody);
				}
			};
		}
	}

	private void getQueryBodyByEntity(List<String> queryBodyList) {
		{
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
				if(!isPositive(ef.getMatchClass())) {
					String queryBodyByEntity = entityFact+"("+ef.getId()+","+ef.getDotNotation()+",AttributeList"+ef.getId()+",ParentList"+ef.getId()+",TemporalChildrenList"+ef.getId()+")";
					{
						List<MatchRelationFilter> mrf = getNegativeRelations(ef.getMatchClass());
						for(MatchRelationFilter rf: mrf) {
							String relationId = rf.getId();
							String sourceId = getIdFrom(rf.getSourceClass());
							String targetId = getIdFrom(rf.getTargetClass());
							queryBodyByEntity += ", "+relationFact+"("+sourceId+",RefList"+relationId+"),"+
										" member(('"+rf.getAssociation().getAssociationName()+"',"+targetId+", "+relationId+"), RefList"+relationId+")";
							String sourceDotNotation = "'"+rf.getSourceClass().getPackageName()+"."+rf.getSourceClass().getClassName()+"'";
							String targetDotNotation = "'"+rf.getTargetClass().getPackageName()+"."+rf.getTargetClass().getClassName()+"'";
							
							if(rf.getTargetClass() == ef.getMatchClass())
								queryBodyByEntity += ", "+entityFact+"("+sourceId+","+sourceDotNotation+",AttributeList"+sourceId+",_,_)";
							else if(rf.getSourceClass() == ef.getMatchClass())
								queryBodyByEntity += ", "+entityFact+"("+targetId+","+targetDotNotation+",AttributeList"+targetId+",_,_)";
						}
					}
					{
						List<MatchRelationFilter> mrf = getNegativeIndirectRelations(ef.getMatchClass());
						for(MatchRelationFilter rf: mrf) {

							String sourceId = getIdFrom(rf.getSourceClass());
							String targetId = getIdFrom(rf.getTargetClass());

							String sourceDotNotation = "'"+rf.getSourceClass().getPackageName()+"."+rf.getSourceClass().getClassName()+"'";
							String targetDotNotation = "'"+rf.getTargetClass().getPackageName()+"."+rf.getTargetClass().getClassName()+"'";
							
							if(rf.getTargetClass() == ef.getMatchClass())
								queryBodyByEntity += ", "+entityFact+"("+sourceId+","+sourceDotNotation+",AttributeList"+sourceId+",ParentList"+sourceId +",_)";
							else if(rf.getSourceClass() == ef.getMatchClass())
								queryBodyByEntity += ", "+entityFact+"("+targetId+","+targetDotNotation+",AttributeList"+targetId+",ParentList"+targetId +",_)";

							queryBodyByEntity += ", memberchk("+ sourceId +", ParentList"+targetId +")";
						}
					}
					{
						List<MatchRelationFilter> mrf = getNegativeTemporalRelations(ef.getMatchClass());
						for(MatchRelationFilter rf: mrf) {
							String targetId = getIdFrom(rf.getTargetClass());										
							{ // referenced apply entities from backward restrictions
								Iterator<ApplyEntityFilter> appiter = getApplyEntityFilters().iterator();
								while(appiter.hasNext()) {
									ApplyEntityFilter aef = appiter.next();
									if(aef.getId().equals(targetId)) {
										queryBodyByEntity += ", "+entityFact+"("+aef.getId()+","+aef.getDotNotation()+
										", AttributeList"+aef.getId() + 
										",[0],[0])";
										
										for(ApplyAttributeFilter maf: aef.getFilterAttributes())
											if(maf.isAtomValue()) {
												String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
												queryBodyByEntity += ", memberchk( "+pair+", AttributeList"+aef.getId()+")";
											}
										break;
									}
								};
							}
							queryBodyByEntity += ", member("+targetId+", TemporalChildrenList"+ef.getId()+")";
						}
					}
					// attributes
					for(MatchAttributeFilter maf: ef.getFilterAttributes()) {
						if(maf.isAtomValue()) {
							String pair = "('_"+maf.getName()+"',"+maf.getAtomValue()+")";
							queryBodyByEntity += ", memberchk( "+pair+", AttributeList"+ef.getId()+")";
						}
					}
					queryBodyList.add(queryBodyByEntity);					
				}
			}
		}
	}

	private List<MatchRelationFilter> getNegativeRelations(MatchClass matchClass) {
		List<MatchRelationFilter> result = new LinkedList<MatchRelationFilter>();
		
		Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
		while(iter.hasNext()) {
			MatchRelationFilter rf = iter.next();
			if(rf.getAssociation() instanceof NegativeMatchAssociation
					&& (rf.getSourceClass() == matchClass || rf.getTargetClass() == matchClass))
				result.add(rf);
		}
		return result;
	}

	private List<MatchRelationFilter> getNegativeIndirectRelations(MatchClass matchClass) {
		List<MatchRelationFilter> result = new LinkedList<MatchRelationFilter>();
		
		Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
		while(iter.hasNext()) {
			MatchRelationFilter rf = iter.next();
			if(rf.getAssociation() instanceof NegativeIndirectAssociation
					&& (rf.getSourceClass() == matchClass || rf.getTargetClass() == matchClass))
				result.add(rf);
		}
		return result;
	}

	private List<MatchRelationFilter> getNegativeTemporalRelations(MatchClass matchClass) {
		List<MatchRelationFilter> result = new LinkedList<MatchRelationFilter>();
		
		Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();
		while(iter.hasNext()) {
			MatchRelationFilter rf = iter.next();
			if(rf.getAssociation() instanceof NegativeBackwardRestriction
					&& (rf.getSourceClass() == matchClass ||
					rf.getTargetClass() == matchClass))
				result.add(rf);
		}
		return result;
	}
	
	private boolean hasNegatives() {
		{
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
				if(!isPositive(ef.getMatchClass())) return true;
			}
		}{
			Iterator<MatchRelationFilter> iter = getMatchRelationFilters().iterator();		
			while(iter.hasNext()) {
				MatchRelationFilter rf = iter.next();
				if(!isPositive(rf.getAssociation())) return true;				
			}
		}{
			Iterator<TemporalRelationFilter> iter = getTemporalRelationFilters().iterator();		
			while(iter.hasNext()) {
				TemporalRelationFilter rf = iter.next();
				if(!isPositive(rf.getAssociation())) return true;				
			}
		}
		return false;
	}

	private boolean hasExist() {
		{
			Iterator<MatchEntityFilter> iter = getMatchEntityFilters().iterator();
			while(iter.hasNext()) {
				MatchEntityFilter ef = iter.next();
				if(isExistPositive(ef.getMatchClass())) return true;
			}
		}
		return false;
	}

	private String getIdFrom(MatchClass sourceClass) {
		for(MatchEntityFilter ef : getMatchEntityFilters()) {
			if(ef.getMatchClass() == sourceClass)
				return ef.getId();
		}
		return "";
	}

	public String getPositiveJoinHead() {
		return _positiveJoinHead;
	}

	public String getPositiveJoinBody() {
		return _positiveJoinBody;
	}

	public String getPositiveJoinHead2() {
		return _positiveJoinHead2;
	}

	public String getPositiveJoinBody2() {
		return _positiveJoinBody2;
	}	
	
	public String getNegativeJoinPredicate() {
		return _negativeJoinPredicate;
	}
	
	public void setNegativeJoinPredicate(String s) {
		_negativeJoinPredicate = s; 
	}
	
	public String getCutPredicate() {
		return _cutPredicate;
	}
	
	public String getQuery() {
		return _query;
	}

	public AbstractFilter getFilter(MatchClass match) {
		for(MatchEntityFilter ef : getMatchEntityFilters()) {
			if(ef.getMatchClass() == match)
				return ef;
			for(MatchAttributeFilter af:  ef.getFilterAttributes()) {
				if(af.getAttribute() == match)
					return af;
			}
		}
		for(MatchRelationFilter rf : getMatchRelationFilters()) {
			if(rf.getAssociation() == match)
				return rf;
		}
		return null;
	}


	public void set_explicitMatchMetaModel(MetaModelDatabase _explicitMatchMetaModel) {
		this._explicitMatchMetaModel = _explicitMatchMetaModel;
	}


	public MetaModelDatabase get_explicitMatchMetaModel() {
		return _explicitMatchMetaModel;
	}


	public void set_explicitMatchModel(InstanceDatabase _explicitMatchModel) {
		this._explicitMatchModel = _explicitMatchModel;
	}


	public InstanceDatabase get_explicitMatchModel() {
		return _explicitMatchModel;
	}
	
	public boolean hasExplicitSource() {
		return (get_explicitMatchMetaModel() != null);
	}


	public void set_matchModel(MatchModel _matchModel) {
		this._matchModel = _matchModel;
	}


	public MatchModel get_matchModel() {
		return _matchModel;
	}


	public List<ApplyEntityFilter> getApplyEntityFilters() {
		return _applyentityFilters;
	}

	public List<TemporalRelationFilter> getTemporalFilters() {
		return getTemporalRelationFilters();
	}

	private List<TemporalRelationFilter> getTemporalRelationFilters() {
		return _temporalrelationFilters;
	}

	public void setPositiveRelations(List<QueryRelation> _positiveRelations) {
		this._positiveRelations = _positiveRelations;
	}

	public List<QueryRelation> getPositiveRelations() {
		return _positiveRelations;
	}

	private void setQueryCutHead(String _queryCutHead) {
		this._queryCutHead = _queryCutHead;
	}

	private String getQueryCutHead() {
		return _queryCutHead;
	}

	private void setQueryHead(String _queryHead) {
		this._queryHead = _queryHead;
	}

	private String getQueryHead() {
		return _queryHead;
	}

	public void setBindingList(@SuppressWarnings("rawtypes") List<Hashtable> _binding) {
		this._binding = _binding;
	}

	@SuppressWarnings("rawtypes")
	public List<Hashtable> getBindingList() {
		return _binding;
	}
	
	public void setExistentialIDs(List<String> IDs) {
		this._existentialIds = IDs;
	}

	public List<String> getExistentialIDs() {
		return _existentialIds;
	}
	
	public List<String> getNegativeIDs() {
		return _negativeIds;
	}
	
	public Hashtable<String, String> getPositiveIndirectIDs() {
		return _positiveIndirectIds; 
	}
	
	public Hashtable<String, String> getNegativeIndirectIDs() {
		return _negativeIndirectIds; 
	}
	
	public List<String> getSolutionsNegative() {
		return _solutionsNegativeComponent;
	}
	
	public List<String> getSolutionsNegativeIndirect() {
		return _solutionsNegativeIndirectComponent;
	}
	
	public List<String> getBackwardLinkIDs() {
		return _backwardLinkIds;
	}

	public MatchAttributeFilter findMatchAttributeFilter(Attribute a) {
		// Percorre todos os MatchEntityFilter e em cada um deles procura um MatchAttributeFilter correspondente ao Attribute dado. O primeiro que encontra, retorna.Nunca haverao dois MatchAttributeFilter para o mesmo Attribute.
		MatchAttributeFilter result = null;
		for(MatchEntityFilter ef : getMatchEntityFilters()) {
			result = ef.findMatchAttributeFilter(a);
			if (result != null) {
				return result;
			}
		}
		assert result == null;
		return null;
	}

	public ApplyAttributeFilter findApplyAttributeFilter(Attribute a) {
		// Percorre todos os ApplyEntityFilters e em cada um deles procura um ApplyAttributeFilter correspondente ao Attribute dado. O primeiro que encontra, retorna. Nunca haverao dois ApplyAttributeFilter para o mesmo Attribute.
		ApplyAttributeFilter result = null;
		for(ApplyEntityFilter ef : getApplyEntityFilters()) {
			result = ef.findApplyAttributeFilter(a);
			if (result != null) {
				return result;
			}
		}
		assert result == null;
		return null;
	}

	public boolean isValid() {
		// An MatchFilter is valid when there is no pair 
		// (MatchEntityFilter, MatchEntityFilter) or (ApplyEntityFilter, ApplyEntityFilter) 
		// that refers to the same instanceEntity.
		
		// This is deactivated because is is now done directly in prolog.
		
//		for (MatchEntityFilter pinedMatchEntityFilter : getMatchEntityFilters()) {
//			for (MatchEntityFilter matchEntityFilter : getMatchEntityFilters()) {
//				if (pinedMatchEntityFilter != matchEntityFilter && pinedMatchEntityFilter.getCurrentEntity() == matchEntityFilter.getCurrentEntity()) {
//					return false;
//				}
//			}
//		}
//		
//		// Now check for ApplyEntityFilters
//		
//		for (ApplyEntityFilter pinedApplyEntityFilter : getApplyEntityFilters()) {
//			for (ApplyEntityFilter applyEntityFilter : getApplyEntityFilters()) {
//				if (pinedApplyEntityFilter != applyEntityFilter && pinedApplyEntityFilter.getCurrentEntity() == applyEntityFilter.getCurrentEntity()) {
//					return false;
//				}
//			}
//		}
		
		return true;
	}

}
