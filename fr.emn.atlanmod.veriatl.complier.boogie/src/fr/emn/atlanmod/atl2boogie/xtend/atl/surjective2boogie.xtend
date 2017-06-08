package fr.emn.atlanmod.atl2boogie.xtend.atl

import fr.emn.atlanmod.atl2boogie.xtend.lib.atl
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Set
import org.eclipse.emf.ecore.EObject
import org.eclipse.m2m.atl.common.ATL.MatchedRule
import org.eclipse.m2m.atl.common.ATL.Module
import org.eclipse.m2m.atl.common.ATL.ModuleElement
import org.eclipse.m2m.atl.common.ATL.OutPatternElement
import org.eclipse.m2m.atl.common.ATL.Rule
import org.eclipse.m2m.atl.common.OCL.OclModelElement
import java.text.SimpleDateFormat
import java.util.Date

class surjective2boogie {
	// Remembering whether out pattern elements are already generated.
	public static Set<String> isPrintedOutPatternElement = new HashSet<String>()
	// The maximum number of out pattern elements among all rules of the given module.
	public static int modDepth = 0;
	
	
	/* 
	 * Reset global static variables of the {@code surjective2boogie} class.
	 */	
	def static reset(){
		modDepth = 0;
		isPrintedOutPatternElement.clear();
	}
		
	 

	/* 
	 * Entry point of generating Boogie axioms for transformation surjectivity.
	 * 
	 * TODO:
	 * - this axiom is inconsistent, consider rewrite as postcondition of every rules:
	 * axiom (forall «atl.genFrameBVElem»: ref, «atl.genHeapInGuardFun»: HeapType :: 
	 * 	(«atl.genFrameBVElem» == null || !read(«atl.genHeapInGuardFun», «atl.genFrameBVElem», alloc)) ==> 
	 * 		«atl.getLinkFunction(i)»_inverse(«atl.genFrameBVElem») == Seq#Empty());   
	 * - check whether new added filter works
	 * */ 
	def static dispatch genModule_surjective(Module module) '''
		/********************************************************************
			@name Boogie axioms for transformation surjectivity 
			@date «new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date)»
			@description Automatically generated by mm2boogie transformation.
		 ********************************************************************/
		  
		«{calDepth(module);}»
		
		  
		«FOR i : (0..modDepth-1)»
			/* generate signature of getTarsBySrcs«i» function and its inverse */
			function «atl.getLinkFunction(i)»(Seq ref): ref;
			function «atl.getLinkFunction(i)»_inverse(ref): Seq ref;
			
			/* generate axioms of getTarsBySrcs«i» function and its inverse */  
			
			// getTarsBySrcs«i» function is injective
			«/* The inverse of an injective function is a also a function. */»
			axiom (forall __refs: Seq ref :: { «atl.getLinkFunction(i)»(__refs) } «atl.getLinkFunction(i)»_inverse(«atl.getLinkFunction(i)»(__refs)) == __refs);
			
			// from s1 to t1,...tn => t1 != t2, .... t1 != tn
			«FOR j : (i+1..<modDepth)» 
			axiom (forall __refs: Seq ref, «atl.genHeapInGuardFun»: HeapType :: 
			  (   («atl.getLinkFunction(i)»(__refs)!=null && read(«atl.genHeapInGuardFun»,«atl.getLinkFunction(i)»(__refs),alloc)) 
			   && («atl.getLinkFunction(j)»(__refs)!=null && read(«atl.genHeapInGuardFun»,«atl.getLinkFunction(j)»(__refs),alloc)) )
			     ==> «atl.getLinkFunction(i)»(__refs) != «atl.getLinkFunction(j)»(__refs));
			«ENDFOR»
			
			// s1 != s2 => getTarsBySrcs«i»(s1) != getTarsBySrcs«i»(s2)
			«FOR j : (i+1..<modDepth)» 
			axiom (forall __refs1, __refs2: Seq ref, «atl.genHeapInGuardFun»: HeapType :: 
			  ( (__refs1 != __refs2) 
			    && («atl.getLinkFunction(i)»(__refs1)!=null && read(«atl.genHeapInGuardFun» ,«atl.getLinkFunction(i)»(__refs1), alloc))
			    && («atl.getLinkFunction(j)»(__refs2)!=null && read(«atl.genHeapInGuardFun» ,«atl.getLinkFunction(j)»(__refs2), alloc)) ) 
			      ==> «atl.getLinkFunction(i)»(__refs1) != «atl.getLinkFunction(j)»(__refs2));
			«ENDFOR»
			
			// getTarsBySrcs«i»({null}) == null
			axiom «atl.getLinkFunction(i)»( Seq#Singleton(null) ) == null;
		«ENDFOR»
		
		// every out pattern element corresponds to a set of input pattern element(s)
		function surj_tar_model($s: HeapType, $t: HeapType): bool{		
			«FOR matchedRule : module.elements.filter(MatchedRule) SEPARATOR "&&"»
				«genModuleElement_surjective(module, matchedRule)»
			«ENDFOR»
		}
	'''

	// matched rule «gen_surjective_core(out, rule)»
	def static genModuleElement_surjective(Module mod, MatchedRule r) '''
	«var i = 0»
	
	«FOR out : r.outPattern.elements SEPARATOR "&&"»
	«IF !(isPrintedOutPatternElement.contains(out.type.name))»
	(forall «out.varName»: ref :: 
	  «out.varName»!=null && read($t, «out.varName», alloc) && dtype(«out.varName») == «(out.type as OclModelElement).model.name»$«out.type.name» ==>
	    «val rules = findRulesGenType(mod, out)»
	    «val keys = rules.keySet»
	    «(0..keys.size-1).map(id | gen_surjective_core(out, keys.get(id), rules.get(keys.get(id)))).join("||")»
	)
	«{isPrintedOutPatternElement.add(out.type.name);""}»
	«ELSE»
	true 
	«ENDIF»
	«{i++;""}»
	«ENDFOR»
	'''
	
	def static gen_surjective_core(OutPatternElement element, MatchedRule rule, ArrayList<Integer> ids) '''
	«(0..ids.size-1).map(id | gen_surjective_sub_core(element, rule, ids.get(id))).join("||")»
	'''
	
	def static gen_surjective_sub_core(OutPatternElement element, MatchedRule rule, int idx) '''
	
	(exists «rule.inPattern.elements.map(e | atl.genInPattern(e, "", ": ref")).join(',')» ::
	  «rule.inPattern.elements.map(e | atl.genInPatternAllocation(e, "$s")).join(' && ')» &&
	   printGuard_«rule.name»($s, «rule.inPattern.elements.map(e | atl.genInPattern(e, "", "")).join(',')») && 
	  	«atl.genOutPattern(rule.inPattern.elements, idx)» == «element.varName» )
	'''
	
	def static findRulesGenType(Module mod, OutPatternElement elem) {
		val tp = elem.type.name
		var rtn = new HashMap<MatchedRule, ArrayList<Integer>>
		for(ModuleElement e : mod.elements){
			if(e instanceof MatchedRule){
				val r = e as MatchedRule
				
				var id = 0	
				for(OutPatternElement out : r.outPattern.elements){
					if(out.type.name == tp){
						if(rtn.get(r)===null){
							var a = new ArrayList<Integer>()
							a.add(id)
							rtn.put(r, a)
						}else{
							rtn.get(r).add(id)
						}
						// break not supported by xtend
					}
					id++
				}
			}
		}
		return rtn
	}

	/* 
	 * Dispatcher of generating Boogie axioms for transformation surjectivity.
	 * */ 
	def static dispatch genModule_surjective(EObject eObject) '''
	// We don't understand «eObject.eClass.name»
	'''
	
	/* 
	 * Calculate the maximum number of out pattern elements among all rules of the given module {@code m}.
	 */	
	def static calDepth(Module m){
		for(ModuleElement e : m.elements){
			if(e instanceof MatchedRule){
				val r = e as MatchedRule
				if(r.outPattern.elements.size > modDepth){
					modDepth = r.outPattern.elements.size
				}
			}
		}
	}
}