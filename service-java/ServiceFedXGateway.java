import java.lang.System ;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
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

import py4j.GatewayServer;

public class ServiceFedXGateway {

  protected enum OutputFormat { STDOUT, JSON, XML; }
  protected OutputFormat outputFormat = OutputFormat.JSON ;

  protected Map<String, String> endpointsNamed ;
  protected Map<String, String> endpointNat ;
  protected Map<String, Boolean> endpointSupportAsk ;

  public ServiceFedXGateway() {

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

    String sres = "{ \"results\" : { \"bindings\" : [] } } ";
    try {
      // startSession
      Config.initialize();
      System.out.println("\n***** Endpoints ***** \n");
      List<Endpoint> endpoints = new ArrayList<Endpoint>();;

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
      TupleQueryResult res = query.evaluate();

      if ( outputFormat == OutputFormat.JSON || outputFormat == OutputFormat.XML ) {

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        TupleQueryResultWriter w ;

        if ( outputFormat == OutputFormat.JSON ) {
          w = new SPARQLResultsJSONWriter(bao);
        } else if ( outputFormat == OutputFormat.XML ) {
          w = new SPARQLResultsXMLWriter(bao);
        } else {
          throw new Exception("Error outputformat:"+outputFormat);
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
      try {
          if (FederationManager.isInitialized()) {
            FederationManager.getInstance().shutDown();
          }
          //Config.reset();
        } catch (Exception e2) {
          System.err.println("Caught FedXException: " + e2.getMessage());
          throw e ;
      }
      throw e ;
    } finally {
      System.out.println("** runQuery Done. ** ");
    }
    return sres;
  }

  public static void main(String[] args) {
    ServiceFedXGateway app = new ServiceFedXGateway();
    // app is now the gateway.entry_point
    GatewayServer server = new GatewayServer(app);
    server.start();
  }
}
