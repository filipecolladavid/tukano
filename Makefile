DOCKERUSER ?= fdavidfctnova
KUBECTL = $(if $(CLOUD),kubectl, minikube kubectl --)

deploy: namespace secret postgres redis blob tukano
namespace:
	- $(KUBECTL) delete ns tukano
	- $(KUBECTL) create namespace tukano
	- $(KUBECTL) config set-context --current --namespace=tukano
secret:
	- $(KUBECTL) delete -f kubernetes/configmap.yaml
	- $(KUBECTL) apply -f kubernetes/configmap.yaml
postgres:
	- $(KUBECTL) delete -f kubernetes/postgresql.yaml
	- $(KUBECTL) apply -f kubernetes/postgresql.yaml
redis:
	- $(KUBECTL) delete -f kubernetes/redis.yaml
	- $(KUBECTL) apply -f kubernetes/redis.yaml
blob:
	- $(KUBECTL) delete -f kubernetes/blobs.yaml
	- $(KUBECTL) apply -f kubernetes/blobs.yaml
tukano:
	- mvn clean compile package
	- docker build -t $(DOCKERUSER)/tukano-webapp .
	- docker push $(DOCKERUSER)/tukano-webapp
	- $(KUBECTL) delete -f kubernetes/tukano_deployment.yaml
	- $(KUBECTL) apply -f kubernetes/tukano_deployment.yaml
	# In mac need to run this command manually
	$(if $(CLOUD),,minikube service tukano-rest-api -n tukano)
	$(if $(CLOUD),,minikube service minio-service -n tukano)

