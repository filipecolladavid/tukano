package tukano.api;

import jakarta.ws.rs.core.Cookie;

public interface Auth {
    String NAME = "auth";
    String COOKIE_KEY = "tukano-auth";

    Result<String> login();

    Result<String> authenticate(String username, String password);

    Result<String> getVersion2(Cookie cookie);
}
