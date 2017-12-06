package com.fluidops.fedx.provider;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;

import com.fluidops.fedx.Config;
import com.fluidops.fedx.structures.Endpoint;

import org.eclipse.rdf4j.common.iteration.Iterations;

/**
 * Convenience methods for {@link Endpoint} providers
 * 
 * @author Andreas Schwarte
 *
 */
public class ProviderUtil {
	private static final Logger log = LoggerFactory.getLogger(ProviderUtil.class);
	/**
	 * Checks the connection by submitting a SPARQL SELECT query:
	 * 
	 * SELECT * WHERE { ?s ?p ?o } LIMIT 1
	 * 
	 * Throws an exception if the query cannot be evaluated
	 * successfully for some reason (indicating that the 
	 * endpoint is not ok)
	 * 
	 * @param repo
	 * @throws RepositoryException
	 * @throws QueryEvaluationException
	 * @throws MalformedQueryException
	 */
	public static long checkConnectionIfConfigured(Config cfg, Repository repo, List<String> graph, List<String> namedGraph) {
		
		if (!cfg.isValidateRepositoryConnections()) {
			return 0;
		}
			
		long startTime = System.currentTimeMillis();
		
		RepositoryConnection conn = repo.getConnection();		
		try {
			//log.info("===========> checkConnectionIfConfigured-->prepareTupleQuery<========================");
						
			/* OFI */
			StringBuilder from = new StringBuilder();
			for ( String g : graph ) {
				from.append("FROM <"+g+"> ");
			}

			for ( String g : namedGraph ) {
				from.append("FROM NAMED <"+g+"> ");
			}
			/* FIN OFI */
			
			TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, "SELECT * "+ from.toString()+" WHERE { ?s ?p ?o } LIMIT 1");
			TupleQueryResult qRes = null;
			try {
				qRes = query.evaluate();
				if (!qRes.hasNext()) {
					log.warn("No data in provided repository (" + repo + ")");
				}
				while (qRes.hasNext()) qRes.next();
				
			} finally {
				if (qRes != null) {
					Iterations.closeCloseable(qRes);
				}
			}			
		} finally {			
			conn.close();
		}
		return System.currentTimeMillis() - startTime;
	}
}
