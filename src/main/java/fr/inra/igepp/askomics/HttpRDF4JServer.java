package fr.inra.igepp.askomics;


import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.rdf4j.http.protocol.Protocol;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigUtil;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.repository.manager.SystemRepository;
import org.eclipse.rdf4j.repository.sail.config.SailRepositoryConfig;
import org.eclipse.rdf4j.sail.inferencer.fc.config.ForwardChainingRDFSInferencerConfig;
import org.eclipse.rdf4j.sail.memory.config.MemoryStoreConfig;

/**
 * @author Herko ter Horst
 */
public class HttpRDF4JServer {

	private static final String HOST = "localhost";

	private static final int PORT = 4040;

	private static final String TEST_REPO_ID = "Test";

	private static final String TEST_INFERENCE_REPO_ID = "Test-RDFS";

	private static final String RDF4J_CONTEXT = "/rdf4j";

	private static final String SERVER_URL = "http://" + HOST + ":" + PORT + RDF4J_CONTEXT;

	public static String REPOSITORY_URL = Protocol.getRepositoryLocation(SERVER_URL, TEST_REPO_ID);

	private final Server jetty;

	public HttpRDF4JServer() {
		System.clearProperty("DEBUG");

		jetty = new Server();

		ServerConnector conn = new ServerConnector(jetty);
		conn.setHost(HOST);
		conn.setPort(PORT);

		//WebAppContext webapp = new WebAppContext();
		//webapp.addSystemClass("org.slf4j.");
		//webapp.addSystemClass("ch.qos.logback.");
		//webapp.setContextPath(RDF4J_CONTEXT);
		// warPath configured in pom.xml maven-war-plugin configuration
		//webapp.setWar("./target/rdf4j-server");
		//jetty.setHandler(webapp);
	}

	public void start()
		throws Exception
	{
		File dataDir = new File(System.getProperty("user.dir") + "/target/datadir");
		dataDir.mkdirs();
		System.setProperty("org.eclipse.rdf4j.appdata.basedir", dataDir.getAbsolutePath());

		jetty.start();
		 // Dump the server state
        System.out.println(jetty.dump());
        
		createTestRepositories();
		jetty.join();
	}

	public void stop()
		throws Exception
	{
		Repository systemRepo = new HTTPRepository(
				Protocol.getRepositoryLocation(SERVER_URL, SystemRepository.ID));
		RepositoryConnection con = systemRepo.getConnection();
		try {
			con.clear();
		}
		finally {
			con.close();
		}

		jetty.stop();
		System.clearProperty("org.mortbay.log.class");
	}

	private void createTestRepositories()
		throws RepositoryException, RepositoryConfigException
	{
		Repository systemRep = new HTTPRepository(
				Protocol.getRepositoryLocation(SERVER_URL, SystemRepository.ID));

		// create a (non-inferencing) memory store
		MemoryStoreConfig memStoreConfig = new MemoryStoreConfig();
		SailRepositoryConfig sailRepConfig = new SailRepositoryConfig(memStoreConfig);
		RepositoryConfig repConfig = new RepositoryConfig(TEST_REPO_ID, sailRepConfig);

		RepositoryConfigUtil.updateRepositoryConfigs(systemRep, repConfig);

		// create an inferencing memory store
		ForwardChainingRDFSInferencerConfig inferMemStoreConfig = new ForwardChainingRDFSInferencerConfig(
				new MemoryStoreConfig());
		sailRepConfig = new SailRepositoryConfig(inferMemStoreConfig);
		repConfig = new RepositoryConfig(TEST_INFERENCE_REPO_ID, sailRepConfig);

		RepositoryConfigUtil.updateRepositoryConfigs(systemRep, repConfig);
	}
	
	public static void main(String[] args) {
		HttpRDF4JServer server = new HttpRDF4JServer();
		
		try {
			System.out.println(" ** start-server **");
			server.start();
		}
		catch (Exception e) {
			try {
				server.stop();
			} catch (Exception e1) {
				e1.printStackTrace();
				System.err.println("----------------------------------------------");
				System.err.println(e1.getMessage());
			}
			e.printStackTrace();
			System.err.println("----------------------------------------------");
			System.err.println(e.getMessage());
			
		}
	}
}
