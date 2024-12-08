package tukano.api.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path(RestAuth.PATH)
public interface RestAuth {
    static final String PATH = "/login";
    static final String USER = "username";
    static final String PWD = "password";
    static final String COOKIE_KEY = "TukanoSession";
    static final String REDIRECT_TO_AFTER_AUTH = "/index.html";

    @GET
    @Produces(MediaType.TEXT_HTML)
    String login();

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    Response login(@FormParam(USER) String username, @FormParam(PWD) String password);
}
