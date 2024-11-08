# Tukano SSC 24/25
This repository contains the source code for the first project
of the Cloud Computing System course.

## Students:
Filipe Colla David - 70666 - f.david@campus.fct.unl.pt <br>
Victor Ditadi Perdigão -  70056 - v.perdigao@fct.unl.pt

## Usage
There are mainly two ways to use this repository:

### Automated:
This allows to create the resources needed, except for the 
SQL database.
Steps:
- Check Automated Deployment directory, and read README.md
- Copy the contents of the ```azureprops-northeurope.props```
to ```src/main/resources/deployment.props```. See the ```PROPS```
section

### Manual
You'll need to deploy the resources manually

### Props
This project uses a properties file with all the information needed
to run different types of instances of the project.
```
ENV=cloud
USER_DB_TYPE=NOSQL
CACHE=false
# Date : 11/6/24, 9:55 PM
# BLOBS
BLOB_STORE_CONNECTION=DefaultEndpointsProtocol=https;AccountName=sto70666northeurope;AccountKey=UKcoYV02f/Ebef5w0mJmuncnyrBmpiFN3tuoo5bBC2oDUY64r5PKJUQiEoreZ1sJlAC1vce0Y3vi+AStmq5ufg==;EndpointSuffix=core.windows.net
STORAGE_ACCOUNT=sto70666northeurope
CONTAINER_NAME=shorts
# COSMOSDB
COSMOSDB_NOSQL_KEY=M9dQhykQHcAUi1BBPEzmkwqeGyjyrbcttWwwyQ7DZjmidJpXfIppT17h8qM2tu4XwfjHrzZwipBtACDbWPXqVQ==
COSMOSDB_NOSQL_URL=https://cosmos70666.documents.azure.com:443/
COSMOSDB_NOSQL_NAME=cosmosdb70666
SHORTS_DB_COLLECTION=shorts
FOLLOWERS_DB_COLLECTION=followers
LIKES_DB_COLLECTION=likes
```
Program Variables: These change the behaviour of the program and need to be changed
according to the environment/conditions in which the program is running.
- ``ENV``: Defines the type of the environment we are running
the program or the tests.<br>
Values:
  - ``local``: When we run the program locally
  - ``cloud``: When we deploy to the Azure cloud
- ``USER_DB_TYPE``: Specifies the type of the users database
  - ``NOSQL``: When the instance of the users database is NOSQL
  - ``SQL``: When the instance of the users database is SQL
- ``CACHE``: To specify if the cache is going to be used (true or false)

Information regarding the cluster instances, these can be copied directly from the
```azureprops-northeurope.props``` mentioned earlier
Blobs
- ``BLOB_STORE_CONNECTION``: The connection details for the blob
- ``STORAGE_ACCOUNT``: The name of the storage account associated with the blob
- ``CONTAINER_NAME``: The name of the container of the blob

Cosmos
- ``COSMOSDB_NOSQL_KEY``: The CosmosDB connection key
- ``COSMOSDB_NOSQL_URL``: The url of the database
- ``COSMOSDB_NOSQL_NAME``: The name of the database
- ``SHORTS_DB_COLLECTION``: The name of the collection to shorts data models 
- ``FOLLOWERS_DB_COLLECTION``: The name of the collection to data models
- ``LIKES_DB_COLLECTION``: The name of the collection to likes data models

### Running
See the Makefile
