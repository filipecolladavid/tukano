package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;
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
	}

	public TukanoRestServer() {
		serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);

		Token.setSecret(Args.valueOf("-secret", ""));
//		String configPath = System.getenv("CONFIG_PATH");
//		if (configPath == null) {
//			configPath = ""; // Default for docker can't use ENV
//		}
		Log.info(String.format("Tukano Server ready @ %s\n", serverURI));
	}

	@Override
	public Set<Class<?>> getClasses() {
		resources.add(RestBlobsResource.class);
		resources.add(RestUsersResource.class);
		resources.add(RestShortsResource.class);
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}