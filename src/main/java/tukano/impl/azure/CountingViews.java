package tukano.impl.azure;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.util.Optional;

public class CountingViews {

    private static final String CONTAINER_NAME = "shorts";
    private static final String DATABASE_NAME = "tukano";
    private static final String SHORTS_DB_COLLECTION = "shorts";

    @FunctionName("fun70666northeurope")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "rest/shorts/{shortId}")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("shortId") String shortId,
            final ExecutionContext context) {

        /**
         * az functionapp config appsettings set --name fun70666northeurope --resource-group rg70666-northeurope --settings "AzureCosmosDBConnection=AccountEndpoint=https://cosmos70666.documents.azure.com:443/;AccountKey=rGxWsHzVOAptJD98PW7IX7fyncLkh3z8ygkA7l6i4aiTSzs9tsw76hsmqRwEfdLJ7SIKNfOajOcLACDbeRefOQ==;"
         * mvn azure-functions:package
         * mvn azure-functions:deploy
         */
        String endpoint = System.getenv("AzureCosmosDBConnection");

        context.getLogger().info("Processing view count for short: " + shortId);

        if (shortId == null || shortId.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Invalid shortId")
                    .build();
        }
        return request.createResponseBuilder(HttpStatus.OK)
                .body("View count incremented successfully.")
                .build();
    }
}