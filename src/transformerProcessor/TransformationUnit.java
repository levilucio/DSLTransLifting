package transformerProcessor;

import emfInterpreter.instance.InstanceDatabase;
import emfInterpreter.metamodel.MetaModelDatabase;

import java.util.Hashtable;

public abstract class TransformationUnit {
	private boolean _processed;
	private boolean _valid;
	private String _classdir;

	private InstanceDatabase _idatabase;
	private MetaModelDatabase _mdatabase;
	// _presenceconds is used in the lifted version of DSLTrans
	protected Hashtable<String, String> _presenceCondsDatabase;
	// _featureFormula is used in the lifted version of DSLTrans
	// to store the initial  propositional logic formula over all the features
	String _featureFormula;
	
	
	TransformationUnit(String classdir) {
		setClassdir(classdir);
		setProcessed(false);
		setValid(true);
	}

	public void setProcessed(boolean _processed) {
		this._processed = _processed;
	}

	public boolean isProcessed() {
		return _processed;
	}
	
	public void setValid(boolean _valid) {
		this._valid = _valid;
	}

	public boolean isValid() {
		return _valid;
	}

	public abstract void Check();
	
	public void setDatabase(InstanceDatabase _idatabase) {
		this._idatabase = _idatabase;
	}

	public InstanceDatabase getDatabase() {
		return _idatabase;
	}

	public void setMetaDatabase(MetaModelDatabase _mdatabase) {
		this._mdatabase = _mdatabase;
	}

	public MetaModelDatabase getMetaDatabase() {
		return _mdatabase;
	}

	public void setClassdir(String _classdir) {
		this._classdir = _classdir;
	}

	public String getClassdir() {
		return _classdir;
	}
	
	public abstract String getMetamodelIdentifier();
	
	// the following methods are used for the lifted version of DSLTrans
	public Hashtable<String, String> getPresenceConds(){
		return _presenceCondsDatabase;
	}

	public void setPresenceConds(Hashtable<String, String> presenceCondsDatabase){
		_presenceCondsDatabase = presenceCondsDatabase;
	}

	public String getFeatureFormula(){
		return _featureFormula;
	}

	public void setFeatureFormula(String featureFormula){
		_featureFormula = featureFormula;
	}
}
