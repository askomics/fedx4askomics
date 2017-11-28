# fedx4askomics
AskOmics service to manage federated queries throught [FedX](http://www.fluidops.com/en/company/knowledge/open_source.php) and [Py4j](https://www.py4j.org)

## Docker image

### Requirements

+ docker


## Local installation

### Requirements

+ python3
+ pip3
+ fedx
+ java
+ py4j

### Download & Install FedX

```
wget http://www.fluidops.com/downloads/collateral/FedX%203.1.zip
unzip FedX\ 3.1.zip -d fedx
```

## Install Py4J

```
sudo pip3 install py4j
```

## Initialize Java

```
export JAVA_HOME=/opt/java/jdk1.8.0_131
export PATH=/opt/java/jdk1.8.0_131/bin/:$PATH
```

## Compile Service Java

```
javac -cp /usr/local/share/py4j/py4j0.10.4.jar:./fedx/lib/* -d class/ service-java/ServiceFedXGateway.java
javac -cp /usr/local/share/py4j/py4j0.10.4.jar:./fedx/lib/* -d class/ service-java/ServiceFedXHttp.java 
```

## Execute service

```
java -cp ./class/:/usr/local/share/py4j/py4j0.10.4.jar:./fedx/lib/* ServiceFedXGateway
java -cp ./class/:/usr/local/share/py4j/py4j0.10.4.jar:./fedx/lib/* ServiceFedXHttp -e test -p 4040
```

## Execute exemple

```
python3 client-python/exemple_sparql_request.py
```

```
curl -i --data-urlencode endpoints="http://localhost:8890/sparql" --data-urlencode query="select * where {?a ?b ?c } limit 10" -H "Content-Type: application/sparql-query" -G  http://localhost:4040/test
```

