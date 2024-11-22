FROM tomcat:10.0-jdk17-openjdk

WORKDIR /usr/local/tomcat

COPY ../target/*.war webapps/

EXPOSE 8080

CMD ["catalina.sh", "run"]