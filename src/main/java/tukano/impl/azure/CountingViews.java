package tukano.impl.azure;

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
                    route = "shorts/{shortId}/view")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("shortId") String shortId,
            final ExecutionContext context) {

        context.getLogger().info("Processing view count for short: " + shortId);
        return new HttpResponseMessage() {
            @Override
            public HttpStatusType getStatus() {
                return null;
            }

            @Override
            public String getHeader(String key) {
                return "";
            }

            @Override
            public Object getBody() {
                return null;
            }
        };
    }
}