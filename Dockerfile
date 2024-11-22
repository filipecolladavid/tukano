# Use an official Tomcat base image with JDK 17
FROM tomcat:10.0-jdk17-openjdk-slim

# Set the working directory in the container
WORKDIR /usr/local/tomcat

COPY target/*.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]