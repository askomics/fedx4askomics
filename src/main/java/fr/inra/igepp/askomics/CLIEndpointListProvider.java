package fr.inra.igepp.askomics;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fluidops.fedx.Config;
import com.fluidops.fedx.EndpointListProvider;
import com.fluidops.fedx.FedX;
import com.fluidops.fedx.exception.FedXException;
import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.util.EndpointFactory;

public class CLIEndpointListProvider implements EndpointListProvider {

	protected List<Endpoint> endpoints = new ArrayList<Endpoint>();
	protected GraphBuilder gb ;
	
	protected String graph;
	protected String namedGraph;
	
	protected Config config = null;
	
	protected Map<String, String> endpointsNamed         ; /* remote external endpoint */
	protected Map<String, String> endpointsNamedAskomics ; /* remote askomics endpoint */
	protected Map<String, String> endpointNat ;
	protected Map<String, Boolean> endpointSupportAsk ;

	public void init() {
		endpointsNamed = new HashMap<String, String>();
		endpointsNamedAskomics = new HashMap<String, String>();
		endpointSupportAsk = new HashMap<String, Boolean>();
		endpointNat = new HashMap<String, String>();
	}
	
	public String getGraph() {
		return graph;
	}

	public void setGraph(String graph) {
		this.graph = graph;
	}
	
	public String getNamedGraph() {
		return namedGraph;
	}

	public void setNamedGraph(String namedGraph) {
		this.namedGraph = namedGraph;
	}

	protected void addEndpoint(String name, String url) {
		endpointsNamed.put(name, url);
		endpointSupportAsk.put(name,true);
		//this.gb.updateListGraph(url);
		System.out.print("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++ SIZE:"+this.gb.nbEndpoint());
	}
	
	protected void addEndpointAskomics(String name, String url) {
		endpointsNamedAskomics.put(name, url);
		endpointSupportAsk.put(name,true);
		//this.gb.updateListGraph(url);
	}

	protected void addEndpoint(String name, String url, Boolean supportAsk) {
		endpointsNamed.put(name, url);
		endpointSupportAsk.put(name,supportAsk);
		//this.gb.updateListGraph(url);
	}
	
	protected void addEndpointAskomics(String name, String url, Boolean supportAsk) {
		endpointsNamedAskomics.put(name, url);
		endpointSupportAsk.put(name,supportAsk);
		//this.gb.updateListGraph(url);
	}

	protected void removeEndpoint(String name) {
		if (endpointsNamed.containsKey(name)) {
			endpointsNamed.remove(name);
			endpointSupportAsk.remove(name);
		}
		if (endpointsNamedAskomics.containsKey(name)) {
			endpointsNamedAskomics.remove(name);
			endpointSupportAsk.remove(name);
		}
	}

	protected void removeAllEndpoints() {
		endpointsNamed.clear();
		endpointsNamedAskomics.clear();
		endpointSupportAsk.clear();
	}

	protected int nbEndpoints() {
		return endpointsNamed.size()+endpointsNamedAskomics.size();
	}

	public void addNativeEndpoint(String name,String path) {
		endpointNat.put(name, path);
		endpointSupportAsk.put(name,true);
	}

	
	public CLIEndpointListProvider(Config config) {
		System.out.println(" ================================> CLIEndpointListProvider Constructor <===========================================");
		this.config = config;
		System.out.println(config);
		this.gb= new GraphBuilder(); 
	}
	
	//@Override
	public List<Endpoint> getEndpoints(FedX federation) {
		System.out.println(" ================================> getEndpoints <===========================================");
		
		System.out.println("====================================== GRAPH property ===============================================");
		
		for (Map.Entry<String, String> entry : endpointsNamed.entrySet()) {
			System.out.println(entry.getKey()+" : "+ entry.getValue());
			String endpoint = entry.getValue();
			System.out.println("====================================== "+ endpoint +" ===============================================");
			System.out.println("GRAPH:"+gb.getGraphMatchingWithQuery(endpoint, graph));
			String subgraph = gb.getGraphMatchingWithQuery(endpoint, graph);
			String subgraphnamed = gb.getGraphMatchingWithQuery(endpoint, namedGraph);
			endpoints.add( EndpointFactory.loadSPARQLEndpoint(config, federation.getHttpClient(), endpoint, 
					subgraph, subgraphnamed));
		}

		for (Map.Entry<String, String> entry : endpointNat.entrySet()) {
			System.out.println(entry.getKey()+" : "+ entry.getValue() + "(NATIVE)");
			endpoints.add( EndpointFactory.loadNativeEndpoint(config, entry.getValue(), 
					gb.getGraphMatchingWithQuery(entry.getValue(), graph), gb.getGraphMatchingWithQuery(entry.getValue(), namedGraph)));
		}

		// generic checks
		if (endpoints.size() == 0) {
			error("No federation members specified. At least one data source is required.", true);
		}

		if (config.getDataConfig() != null) {
			// currently there is no duplicate detection, so the following is a hint for the user
			// can cause problems if members are explicitly specified (-s,-l,-d) and via the fedx configuration
			if (endpoints.size() > 0) {
				System.out.println("WARN: Mixture of implicitely and explicitely specified federation members, dataConfig used: " + config.getDataConfig());
			}
			try {
				List<Endpoint> additionalEndpoints = EndpointFactory.loadFederationMembers(config, federation.getHttpClient(), new File(config.getDataConfig()));
				endpoints.addAll(additionalEndpoints);
			} catch (FedXException e) {
				error("Failed to load implicitly specified data sources from fedx configuration. Data config is: " + config.getDataConfig() + ". Details: " + e.getMessage(), false);
			}
		}        
		return endpoints;
	}

	//@Override
	public void close() {

	}

	public List<Endpoint> getEndpoints1(FedX federation) {
		// TODO Auto-generated method stub
		return null;
	}
	
	protected void error(String errorMsg, boolean printHelp) {
		System.out.println("ERROR: " + errorMsg);
		if (printHelp) {
			System.out.println("");
			//		printUsage();
		}
		System.exit(1);
	}

}
