# Tukano SSC 24/25
This repository contains the source code for the first project
of the Cloud Computing System course.

## Students:
Filipe Colla David - 70666 - f.david@campus.fct.unl.pt <br>
Victor Ditadi Perdigão -  70056 - v.perdigao@fct.unl.pt

## Usage

### Creating the Docker image
Compile to generate the .war file:
```bash
mvn clean compile package
```

Build the docker image
```bash
docker build -t <docker_hub_username>/<name_image> .
```
<docker_hub_username>: is the username from your docker hub account
<name_image>: is the name of the image.

Push the docker image
```bash
docker push <docker_hub_username>/<name_image>
```

```<docker_hub_username>/<name_image>``` is the image value for the ```tukano_deployment.yaml``` file

### Deploying the microservices (locally)
The following commands assume you got some sort of alias from ```kubectl``` to ```kc```
```bash
cd kubernetes
kc apply -f postgressql.yaml
kc apply -f tukano_deployment.yaml
minikube service tukano-rest-api -n tukano
```
#### 'Hot reload' development
The Tukano Rest API replica set is configured to allways download the docker image. If any changes to the code:
```bash
docker build -t <docker_hub_username>/<name_image> .
docker push <docker_hub_username>/<name_image>
kc delete pod <name_tukano_rest_api>
```
It should come back up with the new image.
