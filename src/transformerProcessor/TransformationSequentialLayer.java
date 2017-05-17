package transformerProcessor;

import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import dsltrans.Layer;
import emfInterpreter.EMFLoader;
import emfInterpreter.instance.InstanceDatabase;
import emfInterpreter.metamodel.MetaEntity;
import emfInterpreter.metamodel.MetaModelDatabase;

public class TransformationSequentialLayer extends TransformationLayer{

	TransformationSequentialLayer(String classdir, TransformationController tc, Layer l) {
			super(classdir, tc, l);
	}

	protected void prepareInputModel() {
		TransformationSource ts = getTransformationSource(this.getPrecedingUnit());
		setMatchMetaModel(ts.getMetaDatabase());
		setMatchModel(ts.getDatabase());
		
		// presence conditions are passed on
		// necessary for the lifted version of DSLTrans
		setPresenceConds(ts.getPresenceConds());
		setFeatureFormula(ts.getFeatureFormula());
	}
	
	protected void prepareOutputModel(TransformationController control, String classpath) {
		Map<String, Object> factorys = control.getFactorys();
		Map<String, Object> metamodels = control.getMetamodels();
		String mmName = this.getMetamodelIdentifier();

		if(this.getPrecedingUnit() instanceof TransformationLayer
				&& metamodels.containsKey(mmName)) {
			
			MetaModelDatabase mmd = (MetaModelDatabase) metamodels.get(mmName);
			InstanceDatabase id = control.getLastDatabase(mmd,this,this.getGroupName());
			if(id != this.getDatabase()) {
				this.setDatabase(id.clone(this.getPresenceConds())); // this.getPrecedingUnit().getDatabase() forward the previous database
				this.setMetaDatabase(mmd);
				return; // were set here...
			}
		}
		
		// create a new one			
		String mmPath = this.getLayer().getMetaModelId().getMetaModelURI();
		EMFLoader loader = new EMFLoader();
		if(!metamodels.containsKey(mmName)) {
			loader.loadMetaModel(getClassdir(), mmPath);
			metamodels.put(mmName,loader.getMetaModelDatabase());
		} else {
			loader.setMetaModelDatabase((MetaModelDatabase) metamodels.get(mmName));
		}
					
		this.setMetaDatabase(loader.getMetaModelDatabase());
		this.setDatabase(new InstanceDatabase());
		if (factorys != null)
			this.getDatabase().setFactorys(factorys);
		
		URL[] urlPath = {};
		List<URL> urlList = new LinkedList<URL>();
		try {
			urlList.add(new File(classpath+"/tempClasses").toURI().toURL());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		urlPath = urlList.toArray(urlPath);

		URLClassLoader customLoader = new URLClassLoader(urlPath,this.getClass().getClassLoader());	
		
		for(MetaEntity me : loader.getMetaModelDatabase().getClasses()) {
			try {
				String packageName = me.getCurrentPackage().substring(1);
				packageName = Character.toUpperCase(me.getCurrentPackage().charAt(0)) + packageName;
				String className = me.getNamespace()+"."+packageName+"Package";
				String factoryName = me.getNamespace()+"."+packageName+"Factory";
//					System.out.println("Factory: "+factoryName);
//					for(String fact : getDatabase().getFactorys().keySet()) {
//						System.out.println("Factory: "+fact);
//					}
				if(!getDatabase().getFactorys().containsKey(className)) {
					Object factory = null;
					//Class<?> cc1 = Class.forName(me.getNamespace()+"."+packageName+"Package",true,customLoader);
					Class<?> cc = Class.forName(className,false,customLoader);
					Field f2 = cc.getField("eINSTANCE");
					factory = (Object)f2.get(cc);
					getDatabase().getFactorys().put(className, factory);
					if(!getDatabase().getFactorys().containsKey(factoryName)) {
						Object factory1 = null;
						Class<?> cc1 = Class.forName(factoryName,false,customLoader);
						Field f21 = cc1.getField("eINSTANCE");
						factory1 = (Object)f21.get(cc1);
						getDatabase().getFactorys().put(factoryName, factory1);
					}									
				}
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		if (factorys != null)
			factorys.putAll(getDatabase().getFactorys());
	}

}