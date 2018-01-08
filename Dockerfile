FROM maven:3.5.2-jdk-8-alpine
MAINTAINER Olivier Filangi "olivier.filangi@inra.fr"

COPY . /usr/local/fedx

#RUN apk add --update git && \
#    git clone https://github.com/askomics/fedx4askomics.git /usr/local/fedx && \
#    cd /usr/local/fedx && \
#    mvn clean dependency:copy-dependencies package

WORKDIR /usr/local/fedx
RUN mvn clean dependency:copy-dependencies package

CMD ["/usr/bin/java", "-d64", "-Xms512m", "-Xmx2g", "-cp", "./target/fedx-0.0.1-SNAPSHOT-jar-with-dependencies.jar", "fr.inra.igepp.askomics.HttpSimpleServer", "-e", "test", "-p", "4040"]
