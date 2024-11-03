# Usage
1. Change TODO values for your own<br>
2. Compile the project
```bash
mvn clean compile assembly:single
```
3. Execute the deployment
```bash
java -cp target/scc2425-mgt-1.0-jar-with-dependencies.jar scc.mgt.AzureManagement
```
4. Execute the generated azure-<region>.sh script to set the environment variables (Need some refactoring on our code)

5. Delete the deployments [OPTIONAL]
```bash
java -cp target/scc2425-mgt-1.0-jar-with-dependencies.jar scc.mgt.AzureManagement --delete
```

