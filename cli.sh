#!/bin/bash

OPTIONS="-Dlog4j.debug -Dlog4j.configuration=log4j.properties"
#OPTION=
java -Xmx1024m -cp bin:target/dependency/*:target/* $OPTIONS com.fluidops.fedx.CLI $*
