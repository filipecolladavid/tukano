package tukano.impl.rest;

import java.util.UUID;
import jakarta.ws.rs.NotAuthorizedException;
import tukano.impl.Cache;

public class SessionManager {
    private static final String SESSION_PREFIX = "session:";
    private static final int SESSION_TTL = 3600;
    private final Cache cache;
    private static SessionManager instance;

    private SessionManager() {
        this.cache = Cache.getInstance();
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null)
            instance = new SessionManager();
        return instance;
    }

    public Session createSession(String username) {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId, username);
        cache.setWithExpiry(SESSION_PREFIX + sessionId, session.toRedisString(), SESSION_TTL);
        return session;
    }

    public Session validateSession(String sessionId, String expectedUsername) {
        if (sessionId == null)
            throw new NotAuthorizedException("No session provided");
        String sessionData = cache.get(SESSION_PREFIX + sessionId);
        if (sessionData == null)
            throw new NotAuthorizedException("Invalid or expired session");
        Session session = Session.fromRedisString(sessionData);
        if (session == null)
            throw new NotAuthorizedException("Malformed session data");
        if (expectedUsername != null && !session.username().equals(expectedUsername))
            throw new NotAuthorizedException("Unauthorized access");
        return session;
    }

    public void invalidateSession(String sessionId) {
        if (sessionId != null)
            cache.delete(SESSION_PREFIX + sessionId);
    }
}
