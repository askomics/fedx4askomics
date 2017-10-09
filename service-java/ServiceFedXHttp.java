import java.lang.System ;
import java.util.*;
import java.io.IOException;

import java.io.File;
import java.io.ByteArrayOutputStream;

import org.apache.log4j.Logger;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.openrdf.query.TupleQuery ;
import org.openrdf.query.TupleQueryResult ;
import org.openrdf.query.MalformedQueryException;

import org.openrdf.query.resultio.TupleQueryResultWriter;
import org.openrdf.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.openrdf.query.resultio.sparqlxml.SPARQLResultsXMLWriter;

import com.fluidops.fedx.Config ;
import com.fluidops.fedx.FedXFactory ;
import com.fluidops.fedx.QueryManager ;
import com.fluidops.fedx.FederationManager ;

import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.structures.SparqlEndpointConfiguration;
import com.fluidops.fedx.util.EndpointFactory ;

import com.fluidops.fedx.exception.FedXException ;

import java.io.*;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.*;

import java.lang.*;

public class ServiceFedXHttp implements HttpHandler {

  protected enum OutputFormat { STDOUT, JSON, XML; }
  protected OutputFormat outputFormat = OutputFormat.JSON ;

  protected String askomicsEndpoint = "";
  protected Boolean askomicsEndpointAskSupported = true;

  protected Map<String, String> endpointsNamed ;
  protected Map<String, String> endpointNat ;
  protected Map<String, Boolean> endpointSupportAsk ;

  public ServiceFedXHttp() {

    // Logger
    Logger l = Logger.getLogger("com.fluidops.fedx");
    Logger rootLogger = Logger.getRootLogger();
    rootLogger.setLevel(Level.INFO);
    l.setLevel(Level.INFO);
    l.addAppender(new ConsoleAppender(new PatternLayout("%5p [%t] (%F:%L) - %m%n")));
    start();
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

  @Override
  public void handle(HttpExchange t) throws IOException {

    System.out.println("Method:"+t.getRequestMethod());
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

    if ( query == null ) {
      t.sendResponseHeaders(401, 0);
      return;
    }

    this.setOutputFormat(format);

    try {
      System.out.println("query-->" + query);

      String sres = this.runQuery(query);

      Headers headers = t.getResponseHeaders();
      headers.set("Content-Type", String.format("application/json; charset=%s", StandardCharsets.UTF_8));

      t.sendResponseHeaders(200, sres.length());
      t.getResponseBody().write(sres.getBytes());
      t.getResponseBody().close();
    }
    catch (Exception e) {
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
      System.out.println("SEND^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
      Headers headers = t.getResponseHeaders();
      headers.set("Content-Type", String.format("text/html; charset=%s", StandardCharsets.UTF_8));
      String errormsg = e.getMessage();
      t.sendResponseHeaders(403,errormsg.length());
      t.getResponseBody().write(errormsg.getBytes());
      t.getResponseBody().flush();
      t.getResponseBody().close();
    }
  }

  public void start() {
    endpointsNamed = new HashMap<String, String>();
    endpointSupportAsk = new HashMap<String, Boolean>();
    endpointNat = new HashMap<String, String>();
  }

  public void addEndpoint(String name, String url) {
    endpointsNamed.put(name, url);
    endpointSupportAsk.put(name,true);
  }

  public void addEndpoint(String name, String url, Boolean supportAsk) {
    endpointsNamed.put(name, url);
    endpointSupportAsk.put(name,supportAsk);
  }

  public void removeEndpoint(String name) {
    if (endpointsNamed.containsKey(name)) {
      endpointsNamed.remove(name);
      endpointSupportAsk.remove(name);
    }
  }

  public void addNativeEndpoint(String name,String path) {
    endpointNat.put(name, path);
    endpointSupportAsk.put(name,true);
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
      // startSession
      Config.initialize();
      System.out.println("\n***** Endpoints ***** \n");
      List<Endpoint> endpoints = new ArrayList<Endpoint>();;

      /* Askomis Endpoint */
      Endpoint epAsko = EndpointFactory.loadSPARQLEndpoint("Askomics User Endpoint", this.askomicsEndpoint) ;
      endpoints.add(epAsko);
      SparqlEndpointConfiguration secAsko = new SparqlEndpointConfiguration();
      secAsko.setSupportsASKQueries(this.askomicsEndpointAskSupported);
      epAsko.setEndpointConfiguration(secAsko);

      for (Map.Entry<String, String> entry : endpointsNamed.entrySet()) {
        System.out.println(entry.getKey()+" : "+ entry.getValue());
        endpoints.add( EndpointFactory.loadSPARQLEndpoint(entry.getKey(), entry.getValue()) );
      }

      for (Map.Entry<String, String> entry : endpointNat.entrySet()) {
        System.out.println(entry.getKey()+" : "+ entry.getValue() + "(NATIVE)");
        endpoints.add( EndpointFactory.loadNativeEndpoint(entry.getKey(), entry.getValue()));
      }
      System.out.println("\n**********\n");
      for ( Endpoint ep : endpoints ) {
        if (endpointSupportAsk.containsKey(ep.getName())) {
          System.out.println("Config SupportAsk:"+endpointSupportAsk.get(ep.getName()));
          SparqlEndpointConfiguration sec = new SparqlEndpointConfiguration();
          sec.setSupportsASKQueries(endpointSupportAsk.get(ep.getName()));
          ep.setEndpointConfiguration(sec);
        }
      }

      FedXFactory.initializeFederation(endpoints);
      TupleQuery query = QueryManager.prepareTupleQuery(sparqlRequest);
      System.out.println("evaluate");
      TupleQueryResult res = query.evaluate();
      System.out.println("ok");
      if ( outputFormat == OutputFormat.JSON || outputFormat == OutputFormat.XML ) {


        TupleQueryResultWriter w ;

        if ( outputFormat == OutputFormat.JSON ) {

          w = new SPARQLResultsJSONWriter(bao);

        } else if ( outputFormat == OutputFormat.XML ) {
          w = new SPARQLResultsXMLWriter(bao);
        } else {
          throw new IOException("Illegal format specified: " + outputFormat );
        }

        w.startQueryResult(res.getBindingNames());


        while (res.hasNext()) {
              w.handleSolution(res.next());
        }

        w.endQueryResult();

        sres = new String( bao.toByteArray(), java.nio.charset.StandardCharsets.UTF_8 );

      } else if ( outputFormat == OutputFormat.STDOUT ) {
          while (res.hasNext()) {
              sres += res.next()+"\n";
          }
      }
      if (FederationManager.isInitialized()) {
        FederationManager.getInstance().shutDown();
      }
    } catch (Exception e) {
      //e.printStackTrace();
      try {
          if (FederationManager.isInitialized()) {
            FederationManager.getInstance().shutDown();
          }
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

    ServiceFedXHttp app = new ServiceFedXHttp();
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

    for (int i=0;i<args.length;i++) {
      System.out.println(args[i]);
      if (args[i].equals("-d") && (i+1<args.length)) {
        if (args[i+1].startsWith("-")) throw new IllegalArgumentException("Missing arg following -d");

        app.askomicsEndpoint = args[i+1] ;
      }
    }

    /* Simple test to have a minimu config */
    if (app.askomicsEndpoint.length() == 0) throw new IllegalArgumentException("Please define an askomics endpoint url !");

    Boolean AskSupported = false ;

    //app.addEndpoint("AskOmics","http://localhost:8890/sparql",true);
    //app.addEndpoint("http://dbpedia", "http://dbpedia.org/sparql",AskSupported);
    app.addEndpoint("SemanticWeb","http://data.semanticweb.org/sparql",AskSupported);

    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
      server.createContext("/test", app);
      server.start();
    } catch (IOException e) {
            // no postData - just reset inputstream
    }
  }
}
