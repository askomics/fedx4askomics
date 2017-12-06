package fr.inra.igepp.askomics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

public class GraphBuilder {
	
	protected HashMap<String,HashMap<String,Boolean>> listGraphByEndpoint ;
	
	public GraphBuilder() {
		listGraphByEndpoint = new HashMap<String,HashMap<String,Boolean>>();
	}
	
	public int nbEndpoint() {
		return listGraphByEndpoint.size();
	}
	
	static public String getGraph(String query, String typeFrom) {
		System.out.println("====================================== getGraph ===============================================");
		
		if ( (typeFrom.toLowerCase() != "from") && (typeFrom.toLowerCase() != "from named")) {
			error("GraphBuilder::getGraph bad args="+typeFrom); 
		}
		
		String from = "";
		String queryWork = query.replaceAll("(?i)from", "from");
		try{
			
			Pattern p = Pattern .compile(typeFrom.toLowerCase()+" <(\\S+)>");
			Matcher m = p.matcher(queryWork);
			//System.out.println(queryWork);
			while (m.find()) {
				System.out.println(m.group(1));
				from = from + m.group(1) + ",";
			}
			
			if ( from.length()>0) {
				from = from.substring(0, from.length() - 1);
			}
			
		}catch(PatternSyntaxException pse){
			System.err.println("PatternSyntaxException ..."+pse.getMessage());
		}
		return from ;
	}
	
	public void updateListGraph(String endpoint) {
		System.out.println("====================================== updateListGraph ===============================================");
		System.out.println("endpoint:"+endpoint);
		Repository db = new SPARQLRepository(endpoint);
		
		db.initialize();
		try (RepositoryConnection conn = db.getConnection()) {
			String queryString = "SELECT DISTINCT ?g WHERE { GRAPH ?g {?g ?b ?c.} }";
			// let's check that our data is actually in the database
			TupleQuery query = conn.prepareTupleQuery(queryString);
			System.out.println("eval....");
			if ( listGraphByEndpoint.containsKey(endpoint) ) {
				listGraphByEndpoint.remove(endpoint);
			}
			HashMap<String,Boolean> lGraph = new HashMap<String,Boolean>();
			try (TupleQueryResult result = query.evaluate()) {
				System.out.println("start....");
				// we just iterate over all solutions in the result...
				while (result.hasNext()) {
				    BindingSet solution = result.next();
				    // ... and print out the value of the variable bindings
				    // for ?s and ?n
				    System.out.println("?g = " + solution.getValue("g"));
				    lGraph.put(solution.getValue("g").toString(),true);
					}
				listGraphByEndpoint.put(endpoint, lGraph);
			    }
			System.out.println("end....");
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(1);
		}
		finally {
			
			// before our program exits, make sure the database is properly shut down.
			db.shutDown();
		}
	}
	
	public String getGraphMatchingWithQuery(String endpoint, String listGraph) {
		String lout = "";
		System.out.println("getGraphMatchingWithQuery   test endpoint:"+endpoint);
		System.out.println(listGraphByEndpoint.toString());
		if ( listGraphByEndpoint.containsKey(endpoint) ) {
			System.out.println("endpoint ok");
			for ( String g : listGraph.split(",")) {
				System.out.println("test g:"+g);
				if ( listGraphByEndpoint.get(endpoint).containsKey(g)) {
					lout = lout + g +",";
					System.out.println("ok");
				} else {
					System.out.println("ko");
				}
			}
			
			if (lout.length()>0) lout = lout.substring(0, lout.length() - 1);
		}
		return lout;
	}
	
	
	
	protected static void error(String errorMsg) {
		System.out.println("ERROR: " + errorMsg);
		System.exit(1);
	}
}
