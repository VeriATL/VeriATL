package datastructure;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;

import fr.emn.atlanmod.atl2boogie.xtend.lib.URIs;
import fr.emn.atlanmod.atl2boogie.xtend.util.CompilerConstants;

public class NodeHelper {

	
	public static Node findRoot(ArrayList<Node> tree){
		for(Node n : tree){
			if(n.parent == null){
				return n;
			}
		}
		return null;
	}
	

	/**
	 * find all the leafs of a particular node {@code n} (in a given proof tree)
	 * */
	public static HashSet<Node> findDescendantLeafs(ArrayList<Node> tree, Node trg){
		
		ArrayList<Node> immediateDescendants = findImmediateDescendants(tree, trg);
		HashSet<Node> descendantLeafs = new HashSet<Node>();
		
		for(Node n : immediateDescendants){
			if(isLeaf(tree, n)){
				descendantLeafs.add(n);
			}
		}
		
		for(Node n : immediateDescendants){
			descendantLeafs.addAll(findDescendantLeafs(tree, n));
		}
		
		return descendantLeafs;
		
	}
	
	
	/**
	 * find a sub-goal {@code n} in the given {@code tree} with exact hypotheses and conclusions
	 * */
	public static Node findSubInCache(List<Node> tree, Node n) {
		
		for (Node curr : tree) {

			boolean conclusion = n.conclusion.equals(curr.conclusion);
			boolean hypotheses = n.hypotheses.containsAll(curr.hypotheses);

			if (conclusion && hypotheses) {
				return curr;
			}
		}

		return null;
	}
	
	
	/**
	 * find all the leafs of a given proof tree
	 * */
	public static ArrayList<Node> findAllLeafs(ArrayList<Node> tree){
		
		ArrayList<Node> nonLeafs = new ArrayList<Node>();
		ArrayList<Node> Leafs = new ArrayList<Node>();
		
		for(Node n : tree){
			if(n.parent != null){
				nonLeafs.add(n.parent);
			}
		}
		
		for(Node n: tree){
			if(!nonLeafs.contains(n)){
				Leafs.add(n);
			}
		}
		
		return Leafs;
		
	}

	
	/**
	 * test if a node {@code trg} is leaf or not in {@code tree}.
	 * */
	public static boolean isLeaf(ArrayList<Node> tree, Node trg){
		
		HashSet<Node> nonLeafs = new HashSet<Node>();
	
		for(Node n : tree){
			if(n.parent != null){
				nonLeafs.add(n.parent);
			}
		}
		
		if(!nonLeafs.contains(trg)){
			return true;
		}else{
			return false;
		}
		
	}
	
	
	/**
	 * find immediate descendant of {@code parent}} in {@code tree}}.
	 * */
	public static ArrayList<Node> findImmediateDescendants(ArrayList<Node> tree, Node parent) {
		ArrayList<Node> children =  new ArrayList<Node>();
		
		for(Node n : tree){
			if(n.parent == parent){
				children.add(n);
			}
		}
		
		return children;
	}

	public static Node findSimplifiedPost(ArrayList<Node> vTree) {
		Node r = findRoot(vTree);
		
		ArrayList<Node> children = findImmediateDescendants(vTree,r);
		
		int count = 0;
		Node next = null;
		
		if(r.getResult() == TriBoolean.UNKNOWN){
			for(Node n : children){
				if(n.getResult() == TriBoolean.UNKNOWN){
					count++;
					next = n;
				}		
			}
			
			if(count == 1){
				ArrayList<Node> temp = new ArrayList<Node>(vTree);
				temp.remove(r);
				temp.removeAll(children);
				next.backUpParent = next.parent;
				next.parent = null;
				temp.add(next);
				return findSimplifiedPost(temp);
			}else {
				return r;	
			}
		}else{
			return r.getParent();
		}
		
		

	}

	

	
	/**
	 * populate the leafs of trg using the src
	 * */
	public static ArrayList<Node> populate(ArrayList<Node> oldTree, ArrayList<Node> curTree, String affectedRule) {
		// copy leaf res from oldTree if any exists
		for(Node curLeaf : findAllLeafs(curTree)) {
			Node cache = NodeHelper.findSubInCache(oldTree, curLeaf);
			
			if(cache != null && !curLeaf.getTraces().contains(affectedRule)){
				curLeaf.setResult(cache.getResult());
			}else{
				curLeaf.setResult(TriBoolean.UNKNOWN);
			}
		}
		
		
		
		//populate curTree bottom-up	
		ArrayList<Node> temp = new ArrayList<Node>();
		for (Node leaf : NodeHelper.findAllLeafs(curTree)) {
			temp.add(leaf);
			curTree.remove(leaf);
		}

		while (curTree.size() != 0) {
			for (Node leaf : NodeHelper.findAllLeafs(curTree)) {
				ArrayList<Node> childrenOfLeaf = NodeHelper.findImmediateDescendants(temp, leaf);
				leaf.setResult(TriBoolean.compute(childrenOfLeaf));
				temp.add(leaf);
				curTree.remove(leaf);
			}
		}
		
		return temp;
	}
	
	/**
	 * self populate the tree using its leafs.
	 * */
	public static ArrayList<Node> repopulate(ArrayList<Node> curTree) {
		ArrayList<Node> temp = new ArrayList<Node>();
		for (Node leaf : NodeHelper.findAllLeafs(curTree)) {
			temp.add(leaf);
			curTree.remove(leaf);
		}

		while (curTree.size() != 0) {
			for (Node leaf : NodeHelper.findAllLeafs(curTree)) {
				ArrayList<Node> childrenOfLeaf = NodeHelper.findImmediateDescendants(temp, leaf);
				leaf.setResult(TriBoolean.compute(childrenOfLeaf));
				temp.add(leaf);
				curTree.remove(leaf);
			}
		}
		
		return temp;
	}

	/**
	 * restore the tree, because `findSimplifiedPost` set parent node to null
	 * */
	public static void restore(ArrayList<Node> tree) {
		for(Node n : tree){
			if(n.getBackUpParent()!=null){
				n.setParent(n.getBackUpParent());
				n.setBackUpParent(null);
			}
		}	
	}




	public static URI printTree(URI tarProj, String post, ArrayList<Node> tree) throws Exception {
		URI output = tarProj.appendSegment(CompilerConstants.TREE).appendSegment(post).appendFileExtension(CompilerConstants.GVEXT);
		
		String content = "digraph G {\n";
		
		for(Node n : tree){
			if(n.parent!=null){
				content += String.format("\t%s_%s -> %s_%s;\n", 
						n.getResult(), 
						n.getId(), 
						n.getParent().getResult(),
						n.parent.getId());
						
			}	
		}
		
		content += "}\n";
		
		URIs.write(output, content);
		
		return output;

	}

	public static void printTreeBasic(URI tarProj, String post, ArrayList<Node> tree) throws Exception {		
		URI output = tarProj.appendSegment(CompilerConstants.TREE).appendSegment(post).appendFileExtension(CompilerConstants.GVEXT);
		
		String content = "digraph G {\n";
		
		for(Node n : tree){
			if(n.parent!=null){
				String nName = n.getName();
				String pName = n.getParent().getName();
				
				if(nName.equals("")){
					nName = "_"+n.getId();
				}
				
				if(pName.equals("")){
					pName = "_"+n.parent.getId();
				}
				
				content += String.format("\t%s -> %s;\n", nName, pName);	
			}	
		}
		
		content += "}\n";

		URIs.write(output, content);
		

	}

	public static void updateTreeBasic(URI tarProj, String post, Map<String, String> map) throws Exception {
		URI file = tarProj.appendSegment(CompilerConstants.TREE).appendSegment(post).appendFileExtension(CompilerConstants.GVEXT);
		
		URIConverter uriConverter = new ExtensibleURIConverterImpl();
		InputStream inputStream = uriConverter.createInputStream(file);
				
		// read in content
		BufferedReader input = new BufferedReader (new InputStreamReader (inputStream));
		String line;
		String content = "";
        while ((line = input.readLine()) != null) {
           if(line.indexOf("}") != -1){
        	   break;
           }else if(line.indexOf("[shape=circle, style=filled, fillcolor=red]")!=-1){
        	   continue;
           }
           else{
        	   content += line+"\n";
           }
        }
		
        input.close();
		
        
        // update content
		for(String k : map.keySet()){
			if(map.get(k).equals("false")){
				content += String.format("%s[shape=circle, style=filled, fillcolor=red]\n", k);
			}
		}
		content += "}\n";

		URI output = tarProj.appendSegment(CompilerConstants.TREE).appendSegment(post).appendFileExtension(CompilerConstants.GVEXT);
		URIs.write(output, content);

	}
	
	public static void clean(String filePath, String ext) {
		
		//TODO delete all gv files.
	}


	/**
	 * find a node with name {@code sub} in the given tree.
	 * @param curTree
	 * @param sub
	 * @return
	 */
	public static Node findNode(ArrayList<Node> tree, String sub) {
		for(Node n : tree){
			if(n.getName().equals(sub)){
				return n;
			}
		}
		return null;
	}
	
	
	/**
	 * union the traces of a list of node
	 */
	public static HashSet<String> UnionTraces(Node self, HashSet<Node> nodes){
		HashSet<String> t = self.traces;
		
		for(Node n : nodes) {
			t.addAll(n.getTraces());
		}
		
		return t;
		
		
		
	}
	
	
	
	

}
