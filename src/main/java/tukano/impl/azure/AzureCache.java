package tukano.impl.azure;

import java.util.logging.Logger;
import static java.lang.String.format;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class AzureCache {
    private static AzureCache instance;
    private static Logger Log = Logger.getLogger(AzureCache.class.getName());

    private static JedisPool jedisPoolInstance;

    private static String RedisHost = System.getenv("REDIS_HOST");
    private static int RedisPort = 6380;
    private static int RedisTimeout = 1000;
    private static String RedisPassword = System.getenv("REDIS_PASSWORD");
    private static boolean RedisSSL = true;

    synchronized public static AzureCache getInstance() {
        if (instance == null)
            instance = new AzureCache();
        return instance;
    }

    private AzureCache() {
        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);

        poolConfig.setBlockWhenExhausted(true);
        jedisPoolInstance = new JedisPool(poolConfig, RedisHost, RedisPort, RedisTimeout, RedisPassword,
                RedisSSL);
    }

    public void set(String key, String value) {
        Log.info(() -> format("cache set: key = %s, value = %s\n", key, value));
        try (var jedis = jedisPoolInstance.getResource()) {
            jedis.set(key, value);
        }
    }

    public void setWithExpiry(String key, String value, int expiry) {
        Log.info(() -> format("cache set: key = %s, value = %s, expiry = %d\n", key, value, expiry));
        try (var jedis = jedisPoolInstance.getResource()) {
            jedis.setex(key, expiry, value);
        }
    }

    public String get(String key) {
        Log.info(() -> format("cache get: key = %s\n", key));
        try (var jedis = jedisPoolInstance.getResource()) {
            return jedis.get(key);
        }
    }

    public void delete(String key) {
        try (var jedis = jedisPoolInstance.getResource()) {
            jedis.del(key);
        }
    }
}
