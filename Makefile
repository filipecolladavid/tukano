run: 
	- mvn clean package && mvn assembly:single && java -cp target/tukano-1-jar-with-dependencies.jar tukano.impl.rest.TukanoRestServer

test:
	- mvn clean compile assembly:single && java -cp target/tukano-1-jar-with-dependencies.jar test.Test
dev:
	-docker run -ti --net=host smduarte/tomcat10
	# - docker run -ti --net=host \
	# 	-v "$${PWD}/tomcat/conf/tomcat-users.xml:/usr/local/tomcat/conf/tomcat-users.xml" \
	# 	smduarte/tomcat10

deploy-local:
	- mvn clean compile package tomcat7:redeploy

deploy-cloud:
	- mvn clean compile package azure-webapp:deploy
