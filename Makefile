DOCKERUSER ?= fdavidfctnova
KUBECTL = $(if $(CLOUD),kubectl, minikube kubectl --)

deploy: namespace postgres tukano
namespace:
	- $(KUBECTL) delete ns tukano
	- $(KUBECTL) create namespace tukano
postgres:
	- $(KUBECTL) delete -f kubernetes/postgresql.yaml
	- $(KUBECTL) apply -f kubernetes/postgresql.yaml
tukano:
	- mvn clean compile package
	- docker build -t $(DOCKERUSER)/tukano-webapp .
	- docker push $(DOCKERUSER)/tukano-webapp
	- $(KUBECTL) delete -f kubernetes/tukano_deployment.yaml
	- $(KUBECTL) apply -f kubernetes/tukano_deployment.yaml
	$(if $(CLOUD),,minikube service tukano-rest-api -n tukano)

