package tukano.impl.rest;

import java.io.InputStream;
import java.net.URI;

import jakarta.inject.Singleton;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.api.rest.RestAuth;
import tukano.impl.JavaUsers;

@Singleton
public class RestAuthResource extends RestResource implements RestAuth {
    private static final String LOGIN_PAGE = "login.html";
    private static final int MAX_COOKIE_AGE = 3600;
    private static final String COOKIE_NAME = "TukanoSession";
    private static final String REDIRECT_AFTER_LOGIN = "users";

    private final Users users;
    private final SessionManager sessionManager;

    public RestAuthResource() {
        this.users = JavaUsers.getInstance();
        this.sessionManager = SessionManager.getInstance();
    }

    @Override
    public String login() {
        try {
            InputStream in = getClass().getClassLoader().getResourceAsStream(LOGIN_PAGE);
            if (in == null) {
                throw new WebApplicationException("Login page not found", Status.NOT_FOUND);
            }
            String content = new String(in.readAllBytes());
            return content;
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException("Failed to load login page", Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response login(String username, String password) {
        try {
            Result<User> result = users.getUser(username, password);
            if (!result.isOK())
                throw new NotAuthorizedException("Invalid credentials");
            Session session = sessionManager.createSession(username);
            NewCookie cookie = new NewCookie.Builder(COOKIE_NAME).value(session.toRedisString()).path("/")
                    .maxAge(MAX_COOKIE_AGE)
                    .secure(false).httpOnly(true).build();
            return Response.seeOther(URI.create(REDIRECT_AFTER_LOGIN)).cookie(cookie).build();
        } catch (Exception e) {
            e.printStackTrace();
            throw new WebApplicationException("Failed to login", Status.INTERNAL_SERVER_ERROR);
        }
    }
}
