package lifting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PresCondNode {
	private List<PresCondNode> children = new ArrayList<PresCondNode>();
	private String key;
	private PresCondNode parent = null;
	
	// associative operators 
	List<String> opers = Arrays.asList("and", "or");
	
	public PresCondNode(String key) {
	    this.key = key;
	}

	public List<PresCondNode> getChildren() {
	    return children;
	}
	
	public void deleteAllChildren() {
		this.children.clear();
	}
	
	public void setChildren(List<PresCondNode> nodes) {
		this.deleteAllChildren();
		this.addChildren(nodes);
	}
	
	public void removeChild(PresCondNode n) {
		this.children.remove(n);
	}
	
	public int numChildren() {
		return children.size();
	}

	public PresCondNode addChild(String key)
	{
	    PresCondNode child = new PresCondNode(key);
	    child.parent = this;
	    this.children.add(child);
	    return child;
	}

	public PresCondNode addChild(PresCondNode child)
	{
		child.parent = this;
	    this.children.add(child);
	    return child;
	}
	
	public void addChildren(List<PresCondNode> nodes) {
		for(PresCondNode n : nodes)
			this.addChild(n);
	}

	public String getKey() {
	    return this.key;
	}

	public void setKey(String key) {
	    this.key = key;
	}
	
	public PresCondNode getParent() {
		return this.parent;
	}
	
	/**
	 * Two trees are equal if they have the same key and identical children.
	 */
	@Override
	public boolean equals(Object o) {
		if(o == this) 
			return true;
		
		if(!(o instanceof PresCondNode) || !this.key.equals(((PresCondNode)o).key)) 
			return false;
		
		// all of one tree's children must be possessed by the other
		// verified by checking if one set of children is subset of the other and equal set size
		for(PresCondNode c : ((PresCondNode)o).getChildren()) {
			if (!this.getChildren().contains(c))
				return false;
		}
		
		return (this.numChildren() == ((PresCondNode)o).numChildren());
	}
	
	/**
	 * Hashcode method also needs to be overwritten with the equals() method.  
	 */
	@Override
	public int hashCode() {
		int hash = 17 * 31 + key.hashCode();
		
		for(PresCondNode c : this.getChildren())
			hash += 7 * c.hashCode();
		
		return hash;
	}
	
	/**
	 * Flattens a tree by having parent adopt grandchildren while discarding child.
	 */
	public void adoptChildrenOf(PresCondNode n) {
		if(this.getChildren().contains(n)) {
			this.addChildren(n.getChildren());
			this.removeChild(n);
		}
	}
	
	/**
	 * Removes duplicate children for trees rooted at "and" and "or".
	 * Handles case in which one child is "true" or "false".
	 */
	public void removeDuplicateChildren() {
		Set<PresCondNode> childSet = new HashSet<PresCondNode>(this.getChildren());
		
		PresCondNode trueNode = new PresCondNode("true");
		PresCondNode falseNode = new PresCondNode("false");
		
		if(this.getKey().equals("and")) {
			childSet.remove(trueNode);
			if(childSet.contains(falseNode)) {
				childSet.clear();
				this.setKey("false");
			}
		}
		
		if(this.getKey().equals("or")) {
			childSet.remove(falseNode);
			if(childSet.contains(trueNode)) {
				childSet.clear();
				this.setKey("true");
			}
		}
		
		this.setChildren(new ArrayList<PresCondNode>(childSet));
	}
	
	/**
	 * Gets rid of double negation.
	 */
	public void removeDoubleNegation() {	
		if(this.getKey().equals("not") && this.getChildren().get(0).getKey().equals("not")) {
			PresCondNode gc = this.getChildren().get(0).getChildren().get(0);
			this.setKey(gc.getKey());
			this.setChildren(gc.getChildren());
		} 
	}
	
	/**
	 * Apply de Morgan's Rule.
	 * When there is a match of the form: (A and (not (A and B))) or of the form: (A or (not (A or B))
	 * these can be shortened to (A and (not B)) and (A or (not B)) respectively.  
	 */
	public void applyDeMorgansRule() {
		List<PresCondNode> toDelete = new ArrayList<PresCondNode>();
		
		if(opers.contains(this.getKey())) {
			for(PresCondNode c : this.getChildren()) {
				if(c.getKey().equals("not")) {
					PresCondNode gc = c.getChildren().get(0);
					
					// the child node of not must have two or more children to work
					// (A and (not (and A))) cannot be simplified into A since it equals false
					if(gc.getKey().equals(this.getKey()) && (gc.numChildren() >= 2)) {
						for(PresCondNode ggc : gc.getChildren()) {
							if(this.getChildren().contains(ggc)) {
								toDelete.add(ggc);
							}
						}
					} 
				}
			}
			
			// remove the designated nodes
			// if now of the form (A and (not (and A))), convert to (A and (not A))
			for(PresCondNode ggc : toDelete) {
				PresCondNode parent = ggc.getParent();
				parent.removeChild(ggc);
				if(parent.numChildren() == 1)
					parent.getParent().adoptChildrenOf(parent);
				else if (parent.numChildren() == 0) {
					this.setKey("false");
					this.deleteAllChildren();
					break;
				}
			}	
		}
	}
	
	
	/**
	 * If there are clauses that make the entire condition 'true' or 'false', 
	 * change the tree accordingly.  
	 * (and (not _F1) _F1) --> false
	 * (or (not _F1) _F1) --> true
	 */
	public void applySimpleReduction() {
		boolean delete_all = false;
		
		for(PresCondNode c : this.getChildren()) {
			if(c.getKey().equals("not")) {
				if(this.getChildren().contains(c.getChildren().get(0))) 
					delete_all = true;
			}
		}
		
		if(delete_all) {
			if(this.getKey().equals("and")) {
				this.setKey("false");
				this.deleteAllChildren();
			} else if (this.getKey().equals("or")) {
				this.setKey("true");
				this.deleteAllChildren();
			}
		}
	}
	
	/**
	 * When a subtree's root has the same operator (and/or) as the root
	 * the root adopts the children of the subtree.
	 * (and (and _F1 _F2) --> (and _F1 _F2)
	 */
	public void flatten() {
		List<PresCondNode> toAdoptFrom = new ArrayList<PresCondNode>();
		
		for(PresCondNode c : this.getChildren()) {
			// if there is a subtree with root with same key as the root key
			// if there is a subtree rooted at 'and' or 'or' with only child
			if((opers.contains(this.key) && this.key.equals(c.key)) || 
					(opers.contains(c.key) && (c.numChildren() == 1))) {
				toAdoptFrom.add(c);
			}
		}
		
		for (PresCondNode c : toAdoptFrom) 
			this.adoptChildrenOf(c);
		
		// in the case where the root of the parent is 'and' / 'or' and has only one child
		// the previous algorithm wouldn't apply to the root
		if((this.parent == null) && opers.contains(this.key) && (this.numChildren() == 1)) {
			PresCondNode currentChild = this.getChildren().get(0);
			this.adoptChildrenOf(currentChild);
			this.setKey(currentChild.getKey());
			this.removeChild(currentChild);
		}
	}
	
	/**
	 * Perform various simplifications to minimize the size of the tree.
	 * @return the condensed tree
	 */
	public PresCondNode condense() {

		if(this.numChildren() > 0) {
			for(PresCondNode c : this.getChildren()) 
				c.condense();
			
			this.removeDoubleNegation();
			this.flatten();
			this.removeDuplicateChildren();
			this.applyDeMorgansRule();
			this.applySimpleReduction();
			this.flatten();
		}
		
		return this;
	}
	
	/*
	 * Convert the tree into a valid N-ary presence condition.  
	 */
	@Override
	public String toString() {
		String repr = this.getKey();
		
		if(this.numChildren() > 0) {
			repr = "(" + repr;
			
			for(PresCondNode c : this.getChildren()) 
				repr += " " + c.toString();
			
			repr += ")";
		}
		
		return repr;
	}
	
	public void printTree() {
		print_tree("", true); // starting from the root
	}
	
	/*
	 * A visual representation of a tree; useful for debugging.
	 */
	private void print_tree(String prefix, boolean last) {
        System.out.println(prefix + "|--" + this.getKey());
        
        for (int i = 0; i < this.numChildren() - 1; i++) 
        	this.getChildren().get(i).print_tree(prefix + (last ? "    " : "│   "), false);
          
        if (this.numChildren() > 0) 
        	this.getChildren().get(this.numChildren() - 1).print_tree(prefix + (last ? "    " : "│   "), true);
    }
	
	/*
	 * Convert a given presence condition into a N-ary tree.
	 */
	public static PresCondNode extract(String pc) {	
		// for a clause of the form (operator (clause_1) (clause_2) ... (clause_n))
		Pattern pattern = Pattern.compile("\\((and|or|xor|not)?\\s(.+)\\)");
		Matcher matcher = pattern.matcher(pc);
		
		// for a single feature like _F1
		if(!matcher.find()) 
			return new PresCondNode(pc);
	
		PresCondNode n = new PresCondNode(matcher.group(1)); // for the operator
	    String rest = matcher.group(2);
	    StringBuilder clauses = new StringBuilder();
	    int bracketCount=0;
	    
	    // separate the rest of the PC into constituent clauses 
	    for (char c : rest.toCharArray()){
	        bracketCount += ((c == '(') ? 1 : ((c == ')') ? -1 : 0));
	        
	        if (c==' ' && bracketCount==0) {
	        	clauses.append('\n');
	        } else {
	            clauses.append(c); // append parentheses to clause as well
	        }
	    }
	    
	    // each clause is a subtree of a tree rooted at the operator
	    for (String clause : clauses.toString().split("\\n")) 
	    	n.addChild(PresCondNode.extract(clause));
	    
		return n;
	}

}
