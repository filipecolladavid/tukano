package test.tukano.clients.rest;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tukano.api.rest.RestAuth;

public class RestAuthClient extends RestClient implements RestAuth {

    public RestAuthClient(String serverURI) {
        super(serverURI, RestAuth.PATH);
    }

    @Override
    public String login() {
        return target.request()
                .get(String.class);
    }

    private Response _login(String username, String password) {
        Form form = new Form()
                .param(RestAuth.USER, username)
                .param(RestAuth.PWD, password);

        return target.request()
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    }

    @Override
    public Response login(String username, String password) {
        try {
            return _login(username, password);
        } catch (Exception e) {
            throw e;
        }
    }
}
