package tukano.impl.rest;

public record Session(String sessionId, String username) {
    public String toRedisString() {
        return String.format("%s:%s", sessionId, username);
    }

    public static Session fromRedisString(String redisStr) {
        if (redisStr == null)
            return null;
        String[] parts = redisStr.split(":");
        if (parts.length != 2)
            return null;
        return new Session(parts[0], parts[1]);
    }
}
