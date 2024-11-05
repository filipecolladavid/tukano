package tukano.impl.azure;

import com.azure.cosmos.models.CosmosItemResponse;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.util.Optional;

public class CountingViews {

    private static final String CONTAINER_NAME = "shorts";
    private static final String DATABASE_NAME = "tukano";

    @FunctionName("fun70666northeurope")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "shorts/{shortId}")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("shortId") String shortId,
            final ExecutionContext context) {

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