package tukano.impl.rest;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import tukano.impl.Token;
import utils.Args;
import utils.IP;

@ApplicationPath("/rest")
public class TukanoRestServer extends Application {

    final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

    static final String INETADDR_ANY = "0.0.0.0";
    static String SERVER_BASE_URI = "http://%s:%s/rest";

    private Set<Object> singletons = new HashSet<>();
    private Set<Class<?>> resources = new HashSet<>();

    public static final int PORT = 8080;

    public static String serverURI;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
        try {
            // Load the logging.properties from the classpath
            InputStream stream = TukanoRestServer.class.getClassLoader()
                    .getResourceAsStream("logging.properties");
            if (stream != null) {
                LogManager.getLogManager().readConfiguration(stream);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TukanoRestServer() {
        System.out.println("TukanoRestServer constructor");
        System.out.println("DB_HOST!!!: " + System.getenv("DB_HOST"));
        System.out.println("DB_PORT!!!: " + System.getenv("DB_PORT"));
        System.out.println(
                "JDBC URL: jdbc:postgresql://" + System.getenv("DB_HOST") + ":" + System.getenv("DB_PORT") + "/tukano");
        serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);

        Token.setSecret(Args.valueOf("-secret", ""));
        Log.info(String.format("Tukano Server ready @ %s\n", serverURI));
    }

    @Override
    public Set<Class<?>> getClasses() {
        resources.add(RestBlobsResource.class);
        resources.add(RestUsersResource.class);
        resources.add(RestShortsResource.class);
        resources.add(RestAuthResource.class);

        resources.forEach(resource -> System.out.println("Registered resource: " + resource.getSimpleName()));

        return resources;
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}
