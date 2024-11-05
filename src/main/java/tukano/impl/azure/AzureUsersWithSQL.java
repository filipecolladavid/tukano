package tukano.impl.azure;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.Token;
import utils.DB;

public class AzureUsersWithSQL implements Users {

    private static Logger Log = Logger.getLogger(AzureUsersWithSQL.class.getName());
    private static Users instance;

    private final AzureCache cache;
    private final Gson gson;

    private static final String USER_CACHE_KEY = "user:";
    private static final String SEARCH_CACHE_KEY = "search:";
    private boolean useCache;

    synchronized public static Users getInstance() {
        if (instance == null)
            instance = new AzureUsersWithSQL();
        return instance;
    }

    private AzureUsersWithSQL() {
        this.cache = AzureCache.getInstance();
        this.gson = new Gson();
        this.useCache = Boolean.parseBoolean(System.getProperty("CACHE"));
    }

    @Override
    public Result<String> createUser(User user) {
        Log.info(() -> format("createUser : %s\n", user));
        if (badUserInfo(user)) {
            return error(BAD_REQUEST);
        }

        //TODO - already checked in badUserInfo ?
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }

        try {
            // TODO - insert user before verifying if it's ok ?
            var newUser = DB.insertOne(user);
            if (newUser.isOK()) {
                if (useCache) {
                    setUserCache(newUser.value());
                    deleteSearchCache("");
                }
                return ok(newUser.value().getUserId());
            }
            return error(CONFLICT);
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<User> getUser(String userId, String pwd) {
        Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));
        Result<User> user = null;
        if (userId == null) {
            return error(BAD_REQUEST);
        }
        if (useCache) {
            User cachedUser = getUserFromCache(userId);
            if (cachedUser != null) {
                Log.info(() -> format("user cache hit: userId = %s\n", userId));
                return ok(cachedUser);
            }
            Log.info(() -> format("user cache miss: userId = %s\n", userId));
        }
        user = DB.getOne(userId, User.class);
        if (user.isOK()) {
            setUserCache(user.value());
        }

        return validatedUserOrError(user, pwd);
    }

    @Override
    public Result<User> updateUser(String userId, String pwd, User other) {
        Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));
        if (badUpdateUserInfo(userId, pwd, other))
            return error(BAD_REQUEST);

        try {
            List<User> listUsers = DB.sql("select * from users where users.\"userId\" = '" + userId + "' LIMIT 1",
                    User.class);
            if (listUsers.isEmpty())
                return error(FORBIDDEN);

            User user = listUsers.get(0);

            User updatedUser = user.updateFrom(other);
            Result<User> result = DB.updateOne(updatedUser);

            if (result.isOK()) {
                if (useCache) {
                    setUserCache(result.value());
                    deleteSearchCache("");
                }
                return ok(result.value());
            }
            return error(CONFLICT);
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<User> deleteUser(String userId, String pwd) {
        Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));
        if (userId == null || pwd == null) {
            return error(BAD_REQUEST);
        }
        try {
            List<User> listUsers = DB.sql("select * from users where users.\"userId\" = '" + userId + "' LIMIT 1",
                    User.class);
            if (listUsers.isEmpty()) {
                return error(NOT_FOUND);
            }

            User user = listUsers.get(0);
            AzureShorts.getInstance().deleteAllShorts(user.userId(), pwd, Token.get(userId));
            AzureBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
            Result<User> result = DB.deleteOne(user);
            if (result.isOK()) {
                if (useCache) {
                    deleteUserCache(user);
                    deleteSearchCache("");
                }
            }
            return ok(user);
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        Log.info(() -> format("searchUsers : patterns = %s\n", pattern));
        try {
            if (useCache) {
                List<User> cachedUsers = getFromSearchCache(pattern);
                if (cachedUsers != null) {
                    Log.info(() -> format("search cache hit: pattern = %s\n", pattern));
                    return ok(cachedUsers);
                }

                Log.info(() -> format("search cache miss: pattern = %s\n", pattern));
            }
            // String query = ""; is redundant
            String query;
            if (pattern == null || pattern.isEmpty()) {
                query = "SELECT * FROM \"users\" u";
            } else {
                query = format("SELECT * FROM \"users\" u WHERE UPPER(u.\"userId\") LIKE '%%%s%%'",
                        pattern.toUpperCase());
            }
            List<User> hits = DB.sql(query, User.class)
                    .stream()
                    .map(User::copyWithoutPassword)
                    .toList();
            if (useCache) {
                setSearchCache(pattern, hits);
            }
            return ok(hits);
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    /**
     * Validates user password
     *
     * @param res the user received
     * @param pwd the password suggested
     * @return user if validated, error otherwise
     */
    private Result<User> validatedUserOrError(Result<User> res, String pwd) {
        if (res.isOK()) {
            return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
        } else
            return res;
    }

    /**
     * Validates a requested user across all fields
     *
     * @param user requested
     * @return true if bad request; false otherwise
     */
    private boolean badUserInfo(User user) {
        return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
    }

    /**
     * Validates information to be updated.
     *
     * @param userId of the requester
     * @param pwd    of the requester
     * @param info   with the new parameters to change
     * @return false if either field is empty or the requester is different from the user to be changed
     */
    private boolean badUpdateUserInfo(String userId, String pwd, User info) {
        return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
    }

    /**
     * Creates the string to query the "user" cache
     *
     * @param userId requested
     * @return the updated query string
     */
    private String getUserCacheKey(String userId) {
        return USER_CACHE_KEY + userId;
    }

    /**
     * Creates the string to query the "search" cache
     *
     * @param pattern to be added
     * @return the updated query string
     */
    private String getSearchCacheKey(String pattern) {
        return SEARCH_CACHE_KEY + pattern;
    }

    /**
     * Deletes a user from the cache
     *
     * @param user to be deleted
     */
    private void deleteUserCache(User user) {
        Log.info(() -> format("deleteUserCache: userId = %s\n", user.getUserId()));
        if (user != null)
            cache.delete(getUserCacheKey(user.getUserId()));
    }

    /**
     * Deletes a recently searched user
     * TODO - what's the use of pattern ??
     * @param pattern to be deleted
     */
    private void deleteSearchCache(String pattern) {
        Log.info(() -> format("deleteSearchCache: pattern = %s\n", pattern));
        if (pattern != null)
            cache.delete(getSearchCacheKey(pattern));
    }

    /**
     * Creates an entry of a user in the cache
     *
     * @param user to be added
     */
    private void setUserCache(User user) {
        Log.info(() -> format("setUserCache: userId = %s\n", user.getUserId()));
        if (user != null)
            cache.set(getUserCacheKey(user.getUserId()), gson.toJson(user));
    }

    /**
     * Creates an entry for a recently searched users
     *
     * @param pattern that was used to search
     * @param users   returned by that pattern
     */
    private void setSearchCache(String pattern, List<User> users) {
        Log.info(() -> format("setSearchCache: pattern = %s\n", pattern));
        if (pattern != null)
            cache.set(getSearchCacheKey(pattern), gson.toJson(users));
    }

    /**
     * Returns a user from the cache
     *
     * @param userId of the requested user
     * @return user if found; null otherwise
     */
    private User getUserFromCache(String userId) {
        var res = cache.get(getUserCacheKey(userId));
        if (res != null)
            return gson.fromJson(res, User.class);
        else
            return null;
    }

    /**
     * Returns a search result from the cache
     *
     * @param pattern to query
     * @return List of users if found; null otherwise
     */
    private List<User> getFromSearchCache(String pattern) {
        var res = cache.get(getSearchCacheKey(pattern));
        if (res != null)
            return gson.fromJson(res, new TypeToken<List<User>>() {
            }.getType());
        else
            return null;
    }
}
