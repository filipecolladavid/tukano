package tukano.impl;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.logging.Logger;
import static java.lang.String.format;

public class Cache {
    private static Cache instance;
    private static Logger Log = Logger.getLogger(Cache.class.getName());

    private static JedisPool jedisPoolInstance;

    private static String REDIS_HOST;
    private static String REDIS_PASSWORD;
    private static int REDIS_PORT;
    private static int REDIS_TIMEOUT = 1000;

    synchronized public static Cache getInstance() {
        if (instance == null) {
            instance = new Cache();
        }
        return instance;
    }

    private Cache() {
        REDIS_HOST = System.getenv("REDIS_HOST");
        REDIS_PASSWORD = System.getenv("REDIS_PASSWORD");
        REDIS_PORT = Integer.parseInt(System.getenv("REDIS_PORT"));
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);

        poolConfig.setBlockWhenExhausted(true);
        jedisPoolInstance = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT, REDIS_TIMEOUT, REDIS_PASSWORD, false);
        Log.info("Connected to Redis");
    }

    /**
     * Stores a (key,value) into the cache.
     *
     * @param key to use to store the value
     * @param value to be stored
     */
    public void set(String key, String value) {
        Log.info(() -> format("cache set: key = %s, value = %s\n", key, value));
        try (var jedis = jedisPoolInstance.getResource()) {
            jedis.set(key, value);
        }
    }

    /**
     * Stores a (key, value) into the cache, with an expiry associated
     *
     * @param key to use to store the value
     * @param value to be stored
     * @param expiry associated with this entry
     */
    public void setWithExpiry(String key, String value, int expiry) {
        Log.info(() -> format("cache set: key = %s, value = %s, expiry = %d\n", key, value, expiry));
        try (var jedis = jedisPoolInstance.getResource()) {
            jedis.setex(key, expiry, value);
        }
    }

    /**
     * Gets a (key,value) from the cache
     *
     * @param key of the value to be fetched
     * @return the value associated with they key; null if doesn't exist
     */
    public String get(String key) {
        Log.info(() -> format("cache get: key = %s\n", key));
        try (var jedis = jedisPoolInstance.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Deletes the key value, given a key
     *
     * @param key to be deleted
     */
    public void delete(String key) {
        try (var jedis = jedisPoolInstance.getResource()) {
            jedis.del(key);
        }
    }

}
