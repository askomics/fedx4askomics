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
fedxService.addEndpoint("http://dbpedia", "http://dbpedia.org/sparql",AskSupported)
fedxService.addEndpoint("SemanticWeb","http://data.semanticweb.org/sparql",AskSupported)

fedxService.setOutputFormat("JSON")

q = """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>
       SELECT ?President ?Party WHERE {
       ?President rdf:type dbpedia-owl:President .
       ?President dbpedia-owl:party ?Party . }""";

res = fedxService.runQuery(q)    
res_json   = json.loads(res)
print("== results == "+str(len(res_json['results']['bindings'])))

q2 = """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>
       SELECT ?Party WHERE {
       <http://dbpedia.org/resource/Francesco_Cossiga> rdf:type dbpedia-owl:President .
       <http://dbpedia.org/resource/Francesco_Cossiga> dbpedia-owl:party ?Party . }""";

res = fedxService.runQuery(q2)   
res_json   = json.loads(res)
print("== results == "+str(len(res_json['results']['bindings'])))
print(res)

gateway.close()
