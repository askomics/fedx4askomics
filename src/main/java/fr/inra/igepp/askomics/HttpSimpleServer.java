package fr.inra.igepp.askomics;

import java.util.*;
import java.io.IOException;

import java.io.File;
import java.io.ByteArrayOutputStream;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQuery ;
import org.eclipse.rdf4j.query.TupleQueryResult ;
import org.eclipse.rdf4j.query.MalformedQueryException;

import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriter;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLWriter;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fluidops.fedx.Config;
import com.fluidops.fedx.EndpointListProvider;
import com.fluidops.fedx.FedX;
import com.fluidops.fedx.FedXConnection;
import com.fluidops.fedx.FedXFactory;
import com.fluidops.fedx.QueryManager;
import com.fluidops.fedx.sail.FedXSailRepository;
import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.util.EndpointFactory ;

import com.fluidops.fedx.exception.* ;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.*;

import java.util.regex.*;

@SuppressWarnings("restriction")
public class HttpSimpleServer implements HttpHandler {

	public static Logger log = LoggerFactory.getLogger(HttpSimpleServer.class);

	protected enum OutputFormat { STDOUT, JSON, XML; }
	protected OutputFormat outputFormat = OutputFormat.JSON ;
	protected CLIEndpointListProvider clients;
	
	
	protected String defaultEndpoint = "";

	Config config = null;
	protected FedXSailRepository repo = null;

	public HttpSimpleServer() {
	}

	public void finalize() {

	}

	public Map<String, String> queryToMap(String query){
		Map<String, String> result = new HashMap<String, String>();
		for (String param : query.split("&")) {
			String pair[] = param.split("=");
			if (pair.length>1) {
				result.put(pair[0], pair[1]);
			}else{
				result.put(pair[0], "");
			}
		}
		return result;
	}

	// @Override
	public void handle(HttpExchange t) throws IOException {
		
		start();
		
		System.out.println("Method:"+t.getRequestMethod());
		String endpoints = "";
		String query = "";
		String results = "json";
		String format = "json";
		String output = "json";


		if ( t.getRequestMethod().toUpperCase().equals("GET")) {
			Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
			query = params.get("query");
		} else if (t.getRequestMethod().toUpperCase().equals("POST")) {

			int ln = t.getRequestBody().available();
			byte [] bt  = new byte [ln];
			t.getRequestBody().read(bt);
			String msg = new String(bt);

			Map<String, String> params = queryToMap(msg);
			query = java.net.URLDecoder.decode(params.get("query"), "UTF-8");
			results = java.net.URLDecoder.decode(params.get("results"), "UTF-8");
			format = java.net.URLDecoder.decode(params.get("format"), "UTF-8");
			output = java.net.URLDecoder.decode(params.get("output"), "UTF-8");

		} else {
			t.sendResponseHeaders(401, 0);
			return;
		}

		if ( (query == null) || (query.trim() == "") ) {
			t.sendResponseHeaders(401, 0);
			return;
		}

		this.setOutputFormat(format);

		System.out.println("query-->" + query);

		this.clients.removeAllEndpoints();

		try{

			Pattern p = Pattern .compile("#\\s*endpoint\\s*,(\\w+),\\s*(\\w+)\\s*,\\s*(\\S+)\\s*,\\s*(true|false)");
			Matcher m = p.matcher(query);

			while (m.find()) {
				System.out.println(" *** Manage Endpoint *** \n"+query.substring(m.start(), m.end()));
				System.out.println("TYPE:"+ m.group(1));
				if ( m.group(1).compareTo("askomics") == 0 ) { 
					this.clients.addEndpointAskomics(m.group(2),m.group(3),Boolean.valueOf(m.group(4)));
				} else {
					this.clients.addEndpoint(m.group(2),m.group(3),Boolean.valueOf(m.group(4)));
				}
			
			}
			
		}catch(PatternSyntaxException pse){
			System.err.println("PatternSyntaxException ..."+pse.getMessage());
		}

		if ( this.clients.nbEndpoints() <= 0 ) {
			this.clients.addEndpoint("default",this.defaultEndpoint,false);
		}

		try {
			
			String sres = runQuery(query);

			Headers headers = t.getResponseHeaders();
			headers.set("Content-Type", String.format("application/json; charset=%s", StandardCharsets.UTF_8));

			t.sendResponseHeaders(200, sres.length());
			t.getResponseBody().write(sres.getBytes());
			t.getResponseBody().close();
		}
		catch (Exception e) {
			System.err.println(" ** Manage exception **");
			System.err.println(e.getMessage());
			/*
      Errors with HTTP Status Code 400 (Bad Request)

      Errors with HTTP Status Code 403 (Forbidden)

      Errors with HTTP Status Code 404 (Not Found)

      Errors with HTTP Status Code 405 (Method Not Allowed)

      Errors with HTTP Status Code 406 (Not Acceptable)

      Errors with HTTP Status Code 409 (Conflict)

      Errors with HTTP Status Code 500 (Internal Server Error)

			 */
			//e.printStackTrace();
			System.out.println(e);
			Headers headers = t.getResponseHeaders();
			headers.set("Content-Type", String.format("text/html; charset=%s", StandardCharsets.UTF_8));
			String errormsg = e.getMessage();
			t.sendResponseHeaders(400,errormsg.length());
			t.getResponseBody().write(errormsg.getBytes());
			t.getResponseBody().flush();
			t.getResponseBody().close();
		}
	}

	public void start() {
		String fedxConfig=null;
		config = new Config(fedxConfig);
		this.clients = new CLIEndpointListProvider(config);
		this.clients.init();
	}

	public void setOutputFormat(String format) {
		if (format.toLowerCase().equals("stdout")) {
			outputFormat = outputFormat.STDOUT;
		} else if (format.toLowerCase().equals("xml")) {
			outputFormat = outputFormat.XML;
		} else if (format.toLowerCase().equals("json")) {
			outputFormat = outputFormat.JSON;
		}
	}
	

	public String runQuery(String sparqlRequest) throws Exception {
		System.out.println("====================================== REQUEST ===============================================");
		System.out.println(sparqlRequest);
		ByteArrayOutputStream bao = new ByteArrayOutputStream();

		String sres = "{ \"results\" : { \"bindings\" : [] } } ";
		try {
			config.set("enableMonitoring","false");
			config.set("debugWorkerScheduler", "false");
			config.set("debugQueryPlan", "false");
			config.set("monitoring.logQueries", "false");
			config.set("monitoring.logQueryPlan", "false");
			config.set("workerThreads", "20");
			
			//TODO: set FROM
			
			this.clients.setGraph(GraphBuilder.getGraph(sparqlRequest,"from"));
			this.clients.setNamedGraph(GraphBuilder.getGraph(sparqlRequest,"from named"));
			//this.clients.setGraph("");
			//this.clients.setNamedGraph("");
			
			repo = FedXFactory.initializeFederation(config, this.clients);
		} catch (FedXException e) {
			error("Problem occured while setting up the federation: " + e.getMessage(), false);
		} catch (Exception e) {
			error("Unkown error occured while setting up the federation: " + e.getMessage(), false);
		}

		SailRepositoryConnection conn = repo.getConnection();
		FedXConnection fconn = (FedXConnection)conn.getSailConnection();
		QueryManager qm = fconn.getQueryManager();

		try {

			//System.out.println(qm.getQueryPlan(sparqlRequest, fconn.getSummary()));
			// setup the federation

			//FedXFactory.initializeFederation(endpoints);
			//TupleQuery query = QueryManager.prepareTupleQuery(sparqlRequest);
			TupleQuery query;
			try {
				query = conn.prepareTupleQuery(sparqlRequest);
			} catch (MalformedQueryException e) {
				throw new QueryEvaluationException("Query is malformed: " + e.getMessage());
			} 

			System.out.println("evaluate");
			log.info(query.toString());
			TupleQueryResult res = query.evaluate();
			System.out.println("evaluate ok");

			if ( outputFormat == OutputFormat.JSON || outputFormat == OutputFormat.XML ) {
				TupleQueryResultWriter w ;

				if ( outputFormat == OutputFormat.JSON ) {

					w = new SPARQLResultsJSONWriter(bao);

				} else if ( outputFormat == OutputFormat.XML ) {
					w = new SPARQLResultsXMLWriter(bao);
				} else {
					throw new IOException("Illegal format specified: " + outputFormat );
				}
				System.out.println("build results");
				w.startQueryResult(res.getBindingNames());

				int iCount = 0;
				while (res.hasNext()) {
					iCount++;
					w.handleSolution(res.next());
				}

				w.endQueryResult();
				System.out.println("build results ok nb res:"+iCount);
				sres = new String( bao.toByteArray(), java.nio.charset.StandardCharsets.UTF_8 );

			} else if ( outputFormat == OutputFormat.STDOUT ) {
				while (res.hasNext()) {
					sres += res.next()+"\n";
				}
			}
			/*
      if (FederationManager.isInitialized()) {
        FederationManager.getInstance().shutDown();
      }
			 */
		} catch (Exception e) {
			e.printStackTrace();
			try {
				/*
          if (FederationManager.isInitialized()) {
            FederationManager.getInstance().shutDown();
          }
				 */
				//Config.reset();
			} catch (Exception e2) {
				System.err.println("Caught FedXException: " + e2.getMessage());
				throw e ;
			}
			System.out.println(e);
			throw e ;
		} finally {
			System.out.println("** runQuery Done. ** ");
		}
		return sres;
	}

	public static void main(String[] args) {

		HttpSimpleServer app = new HttpSimpleServer();
		app.start();

		System.out.println(" -- Manage FedX SPARQL Request for AskOmics -- ");
		System.out.println("");
		System.out.println(" Option ");
		System.out.println(" -d     : AskOmics endpoint user ");
		System.out.println(" -a     : ask supported [true|false], true by default");
		System.out.println(" -p     : port (default 8000)");
		System.out.println(" -e     : Relative path SPARQL endpoint to request this service.");
		System.out.println("");
		System.out.println(" ------------------------------------------------------------------------------ ");

		int port = 8000 ;
		String rpath = "";

		for (int i=0;i<args.length;i++) {
			System.out.println(args[i]);
			if (args[i].equals("-d") && (i+1<args.length)) {
				if (args[i+1].startsWith("-")) throw new IllegalArgumentException("Missing arg following -d");
				app.defaultEndpoint = args[i+1] ;
			}
			if (args[i].equals("-p") && (i+1<args.length)) {
				if (args[i+1].startsWith("-")) throw new IllegalArgumentException("Missing arg following -p");

				port = Integer.parseInt(args[i+1]) ;
			}
			if (args[i].equals("-e") && (i+1<args.length)) {
				if (args[i+1].startsWith("-")) throw new IllegalArgumentException("Missing arg following -p");

				rpath = args[i+1] ;
			}
		}

		//Boolean AskSupported = false ;

		//app.addEndpoint("AskOmics","http://localhost:8890/sparql",true);
		//app.addEndpoint("http://dbpedia", "http://dbpedia.org/sparql",AskSupported);
		//app.addEndpoint("SemanticWeb","http://data.semanticweb.org/sparql",AskSupported);

		//app.addEndpoint("Regine","http://openstack-192-168-100-46.genouest.org/virtuoso/sparql",AskSupported);
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
			server.createContext("/"+rpath, app);
			server.start();
			System.out.println(" =====  start  ===== http://localhost:" + port + "/"+rpath );
		} catch (IOException e) {
			System.err.println(e.getMessage());
			// no postData - just reset inputstream
		}
	}

	/**
	 * Print an error and exit
	 * 
	 * @param errorMsg
	 */
	protected void error(String errorMsg, boolean printHelp) {
		System.out.println("ERROR: " + errorMsg);
		if (printHelp) {
			System.out.println("");
			//		printUsage();
		}
		System.exit(1);
	}
}
