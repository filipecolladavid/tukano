# Need to upload image to Kubernetes Docker repository
FROM smduarte/tomcat10

COPY ../target/*.war /usr/local/tomcat/webapps/

EXPOSE 8080