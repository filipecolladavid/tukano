# Tukano SSC 24/25
This repository contains the source code for the first project
of the Cloud Computing System course.

## Students:
Filipe Colla David - 70666 - f.david@campus.fct.unl.pt <br>
Victor Ditadi Perdigão -  70056 - v.perdigao@fct.unl.pt

## Usage
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