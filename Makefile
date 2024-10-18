test:
	- mvn clean compile assembly:single && java -cp target/tukano-1-jar-with-dependencies.jar test.Test
