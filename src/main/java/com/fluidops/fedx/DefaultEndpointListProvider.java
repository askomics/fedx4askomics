package com.fluidops.fedx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fluidops.fedx.evaluation.concurrent.ControlledWorkerScheduler;
import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.util.EndpointFactory;

public class DefaultEndpointListProvider implements EndpointListProvider {
	private static final Logger log = LoggerFactory.getLogger(ControlledWorkerScheduler.class);
    Collection<String> endpoints;
    
    public DefaultEndpointListProvider(List<String> endpoints) {
        this.endpoints = endpoints;
    }
    
   
    //@Override
    public List<Endpoint> getEndpoints(FedX federation) {
        List<Endpoint> result = new ArrayList<Endpoint>();
        
        for (String url : endpoints) {
        	//TODO: add graph/namedGraph in federation...
        	String graph = "";
        	String namedGraph = "";
        	log.warn("==========================  ADD MANAGE GRAPH/NAMED GRAPH ======================");
            result.add(EndpointFactory.loadSPARQLEndpoint(federation.getConfig(), federation.getHttpClient(), url, graph, namedGraph));
        }
        return result;
    }
    
    /**
     * Remove a member from the federation (internal)
     * 
     * @param endpoint
     * @return
     */
    public boolean removeMember(Endpoint endpoint) {
        return endpoints.remove(endpoint);
    }


    //@Override
    public void close() {
        
    }   
}
