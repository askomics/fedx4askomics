# BUILD

```
mvn clean dependency:copy-dependencies package
```

# HTTP Server

```
java -d64 -Xms512m -Xmx2g -cp ./target/fedx-0.0.1-SNAPSHOT-jar-with-dependencies.jar fr.inra.igepp.askomics.HttpSimpleServer -e test -p 4040
```

# Send HTTP sparqlrequest

## Post Vars

### query

```
#endpoint,<name>,<url>,<supportAsk>
..
PREFIX ...
SELECT ...
```

## sample

```
#endpoint,askomics,askomics-prod,http://url.production.inra.fr/virtuoso/sparql,false
#endpoint,external,Reactome,https://www.ebi.ac.uk/rdf/services/sparql,false
#endpoint,askomics,local,http://localhost:8890/sparql,false
PREFIX : <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#>
PREFIX rdfg: <http://www.w3.org/2004/03/trix/rdfg-1/>
PREFIX prov: <http://www.w3.org/ns/prov#>
PREFIX faldo: <http://biohackathon.org/resource/faldo#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
PREFIX dc: <http://purl.org/dc/elements/1.1/>
PREFIX displaySetting: <http://www.irisa.fr/dyliss/rdfVisualization/display>
SELECT DISTINCT ?transcript1 ?URItranscript1 ?owlClass1 ?URIowlClass1
WHERE {
{
	?URItranscript1 rdf:type <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#transcript>.
	?URItranscript1 rdfs:label ?transcript1.
	?URIowlClass1 rdf:type <http://www.w3.org/2002/07/owl#Class>.
	?URIowlClass1 rdfs:label ?owlClass1.
	?URItranscript1 <http://www.semanticweb.org/irisa/ontologies/2016/1/igepp-ontology#isBlasted> ?URIowlClass1.
}

