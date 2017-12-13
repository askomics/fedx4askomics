/*
 * Copyright (C) 2008-2013, fluid Operations AG
 *
 * FedX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.fluidops.fedx.evaluation;

import java.util.List;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.Var;
import org.eclipse.rdf4j.query.impl.EmptyBindingSet;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;

import com.fluidops.fedx.FedX;
import com.fluidops.fedx.algebra.ExclusiveGroup;
import com.fluidops.fedx.algebra.FilterValueExpr;
import com.fluidops.fedx.evaluation.iterator.BufferedCloseableIterator;
import com.fluidops.fedx.evaluation.iterator.FilteringInsertBindingsIteration;
import com.fluidops.fedx.evaluation.iterator.FilteringIteration;
import com.fluidops.fedx.evaluation.iterator.InsertBindingsIteration;
import com.fluidops.fedx.exception.ExceptionUtil;
import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.structures.SparqlEndpointConfiguration;
import com.fluidops.fedx.util.QueryStringUtil;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;
import org.eclipse.rdf4j.common.iteration.ExceptionConvertingIteration;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * A triple source to be used for (remote) SPARQL endpoints.<p>
 * 
 * This triple source supports the {@link SparqlEndpointConfiguration} for
 * defining whether ASK queries are to be used for source selection.
 * 
 * @author Andreas Schwarte
 *
 */
public class SparqlTripleSource extends TripleSourceBase implements TripleSource {
	
	static Logger log = LoggerFactory.getLogger(SparqlTripleSource.class);
	
	private boolean useASKQueries = true;
	final FederationEvalStrategy strategy;
	
	SparqlTripleSource(FederationEvalStrategy strategy, Endpoint endpoint) {
		super(FedX.getMonitoring(), endpoint);
		this.strategy = strategy;
		if (endpoint.getEndpointConfiguration() instanceof SparqlEndpointConfiguration) {
			SparqlEndpointConfiguration c = (SparqlEndpointConfiguration) endpoint.getEndpointConfiguration();
			this.useASKQueries = c.supportsASKQueries();
		}			
	}
	
	//@Override
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(
			String preparedQuery, RepositoryConnection conn, List<String> graph,List<String> namedGraph,  BindingSet bindings, FilterValueExpr filterExpr)
	{
		//log.info(">>>>>>>>>>>>>>>>>>>>>>>> SparqlTripleSource::getStatements <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
		//log.debug("preparedQuery:"+preparedQuery);
		/* OFI */
		StringBuilder repl = new StringBuilder();
		
		for ( String g : graph) {
			repl.append("FROM <"+g+"> ");
		}

		for ( String g : namedGraph ) {
			repl.append("FROM NAMED <"+g+"> ");
		}
		
		preparedQuery = preparedQuery.replaceFirst("FROM <>", repl.toString());
		
		//log.debug("preparedQuery replace from *"+preparedQuery+"*");
		/* FIN OFI */
		TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, preparedQuery, null);
		
		//log.debug("DISABLE INFERENCE............................");
		//query.setMaxExecutionTime(10);
		disableInference(query);
		
		CloseableIteration<BindingSet, QueryEvaluationException> res=null;
		try {			
			
			// evaluate the query
			monitorRemoteRequest();
			res = query.evaluate();
			
			// apply filter and/or insert original bindings
			if (filterExpr!=null) {
				if (bindings.size()>0) 
					res = new FilteringInsertBindingsIteration(strategy, filterExpr, bindings, res);
				else
					res = new FilteringIteration(strategy, filterExpr, res);
				if (!res.hasNext()) {
					Iterations.closeCloseable(res);
					return new EmptyIteration<BindingSet, QueryEvaluationException>();
				}
			} else if (bindings.size()>0) {
				res = new InsertBindingsIteration(res, bindings);
			}
	
			// in order to avoid licking http route while iteration
			return new BufferedCloseableIterator<BindingSet, QueryEvaluationException>(res);
			
		} catch (QueryEvaluationException ex) {
			log.error("gaph:"+graph.toString());
			log.error("gaphNamed:"+namedGraph.toString());
			log.error(preparedQuery);
			log.error(ex.getMessage());
			Iterations.closeCloseable(res);
			throw ExceptionUtil.traceExceptionSourceAndRepair(strategy.getFedXConnection().getEndpointManager(), conn, ex, "Subquery: " + preparedQuery);			
		}
	}

	//@Override
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(
			StatementPattern stmt, RepositoryConnection conn, List<String> graph,List<String> namedGraph,
			BindingSet bindings, FilterValueExpr filterExpr)
			throws RepositoryException, MalformedQueryException,
			QueryEvaluationException  {
		
		throw new RuntimeException("NOT YET IMPLEMENTED.");
	}

	@Override
	public boolean hasStatements(RepositoryConnection conn, List<String> graph, List<String> namedGraph, Resource subj,
			IRI pred, Value obj, Resource... contexts)
			throws RepositoryException {
		//log.info("hasStatements");
		if (!useASKQueries) {
			StatementPattern st = new StatementPattern(new Var("s", subj), new Var("p", pred), new Var("o", obj));
			try {
				return hasStatements(st, conn, graph, namedGraph, EmptyBindingSet.getInstance());
			} catch (Exception e) {
				throw new RepositoryException(e);
			}
		}		
		return super.hasStatements(conn, graph,namedGraph,  subj, pred, obj, contexts);
	}
	
	//@Override
	public boolean hasStatements(StatementPattern stmt, RepositoryConnection conn, List<String> graph, List<String> namedGraph,
			BindingSet bindings) throws RepositoryException,
			MalformedQueryException, QueryEvaluationException {
		//log.info("hasStatements");
		// decide whether to use ASK queries or a SELECT query
		if (useASKQueries) {
			/* remote boolean query */
			//log.info("graph="+graph);
			String queryString = QueryStringUtil.askQueryString(stmt, graph, namedGraph, bindings);
			//log.info("querystring1="+queryString);
			
			//log.info(conn.getClass().toString());
			BooleanQuery query = conn.prepareBooleanQuery(QueryLanguage.SPARQL, queryString, null);
			
			disableInference(query);
			
			try {
				monitorRemoteRequest();
				//log.info("===================================================--------------------START =============="+conn.toString()+"  query="+queryString);
				//long startTime = System.currentTimeMillis();
				boolean hasStatements = query.evaluate();
				//long endTime = System.currentTimeMillis();
				//System.out.println("That took " + (endTime - startTime) + " milliseconds");
				return hasStatements;
			} catch (QueryEvaluationException ex) {
				throw ExceptionUtil.traceExceptionSourceAndRepair(strategy.getFedXConnection().getEndpointManager(), conn, ex, "Subquery: " + queryString);			
			}
			
		} else {
			/* remote select limit 1 query */
			String queryString = QueryStringUtil.selectQueryStringLimit1(stmt, graph, namedGraph, bindings);
			//log.info("querystring2="+queryString);
			TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
			disableInference(query);
			
			TupleQueryResult qRes = null;
			try {
				monitorRemoteRequest();
				qRes = query.evaluate();
				boolean hasStatements = qRes.hasNext();
				return hasStatements;
			} catch (QueryEvaluationException ex) {
				throw ExceptionUtil.traceExceptionSourceAndRepair(strategy.getFedXConnection().getEndpointManager(), conn, ex, "Subquery: " + queryString);			
			} finally {
				if (qRes!=null)
					qRes.close();
			}
		}
		
	}
	
	@Override
	public boolean hasStatements(ExclusiveGroup group,
			RepositoryConnection conn, BindingSet bindings)
			throws RepositoryException, MalformedQueryException,
			QueryEvaluationException {
	//	log.info("hasStatements");
		if (!useASKQueries) {
			
			/* remote select limit 1 query */
			String queryString = QueryStringUtil.selectQueryStringLimit1(group, bindings);
		//	log.info("querystring="+queryString);
			TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
			disableInference(query);
			
			TupleQueryResult qRes = null;
			try {
				monitorRemoteRequest();
				qRes = query.evaluate();
				boolean hasStatements = qRes.hasNext();
				return hasStatements;
			} catch (QueryEvaluationException ex) {
				throw ExceptionUtil.traceExceptionSourceAndRepair(strategy.getFedXConnection().getEndpointManager(), conn, ex, "Subquery: " + queryString);			
			} finally {
				if (qRes!=null)
					qRes.close();
			}
		}		
		
		// default handling: use ASK query
		return super.hasStatements(group, conn , bindings);
	}

	//@Override
	public boolean usePreparedQuery() {
		return true;
	}

	//@Override
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(
			TupleExpr preparedQuery, RepositoryConnection conn,List<String> graph, List<String> namedGraph,
			BindingSet bindings, FilterValueExpr filterExpr)
			throws RepositoryException, MalformedQueryException,
			QueryEvaluationException {
		
		throw new RuntimeException("NOT YET IMPLEMENTED.");
	}

	//@Override
	public CloseableIteration<Statement, QueryEvaluationException> getStatements(
			RepositoryConnection conn, List<String> graph, List<String> namedGraph, Resource subj, IRI pred, Value obj,
			Resource... contexts) throws RepositoryException,
			MalformedQueryException, QueryEvaluationException
	{
		
		// TODO add handling for contexts
		monitorRemoteRequest();
		RepositoryResult<Statement> repoResult = conn.getStatements(subj, pred, obj, true);
		
		return new ExceptionConvertingIteration<Statement, QueryEvaluationException>(repoResult) {
			@Override
			protected QueryEvaluationException convert(Exception arg0) {
				return new QueryEvaluationException(arg0);
			}
		};		
	}

}
