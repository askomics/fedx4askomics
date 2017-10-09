#!/usr/bin/python3
#
# Before launch this script, Service have to be compiled and started
#
# in the root directory
# compilation : javac -cp .:/usr/local/share/py4j/py4j0.10.4.jar:./fedx/lib/* ServiceFedXGateway.java
# execution   : java -cp .:/usr/local/share/py4j/py4j0.10.4.jar:./fedx/lib/* ServiceFedXGateway
#

from py4j.java_gateway import JavaGateway
import json

gateway = JavaGateway()                   # connect to the JVM
fedxService = gateway.entry_point

fedxService.start()
AskSupported=False
fedxService.addEndpoint("Virtuoso Asko","http://localhost:8890/sparql",False)
fedxService.addEndpoint("Virtuoso Asko2","http://localhost/virtuoso/sparql",False)

fedxService.setOutputFormat("JSON")

q = """PREFIX : <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfg: <http://www.w3.org/2004/03/trix/rdfg-1/>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
PREFIX dc: <http://purl.org/dc/elements/1.1/>
PREFIX displaySetting: <http://www.irisa.fr/dyliss/rdfVisualization/display>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?URIPersonne1 ?Personne1
WHERE {
{
	?URIPersonne1 rdf:type <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#Personne>.
	?URIPersonne1 rdfs:label ?Personne1.
	?URIPersonne1 <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#Sexe> ?URICatSexe1.
	?URICatSexe1 rdfs:label ?Sexe1.
	#VALUES ?URICatSexe1 { <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#F> }.
    FILTER( ?URICatSexe1 = <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#F> || ?URICatSexe1 = <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#M> ).
}
}""";

res = fedxService.runQuery(q)
res_json   = json.loads(res)
print("== results == "+str(len(res_json['results']['bindings'])))
print(res)

gateway.close()
