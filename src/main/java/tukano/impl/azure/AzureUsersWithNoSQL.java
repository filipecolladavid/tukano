package tukano.impl.azure;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.Token;
import utils.DB;

public class AzureUsersWithNoSQL implements Users {
    private static Logger Log = Logger.getLogger(AzureUsersWithNoSQL.class.getName());
    private static Users instance;

    private static String endpoint;
    private static String DB_KEY;
    private static String DB_NAME;
    private static String DB_COLLECTION;

    private static CosmosClient cosmosClient;
    private static CosmosDatabase db;
    private static CosmosContainer usersDB;

    private final AzureCache cache;
    private final Gson gson;

    private static final String USER_CACHE_KEY = "user:";
    private static final String SEARCH_CACHE_KEY = "search:";
    private boolean useCache;

    synchronized public static Users getInstance() {
        if (instance == null) {
            instance = new AzureUsersWithNoSQL();
        }
        return instance;
    }

    private AzureUsersWithNoSQL() {
        AzureUsersWithNoSQL.endpoint = System.getProperty("COSMOSDB_NOSQL_URL");
        AzureUsersWithNoSQL.DB_KEY = System.getProperty("COSMOSDB_NOSQL_KEY");
        AzureUsersWithNoSQL.DB_NAME = System.getProperty("COSMOSDB_NOSQL_NAME");

        this.gson = new Gson();
        this.useCache = Boolean.parseBoolean(System.getProperty("CACHE"));

        AzureUsersWithNoSQL.DB_COLLECTION = "users";
        AzureUsersWithNoSQL.cosmosClient = new CosmosClientBuilder().endpoint(endpoint).key(DB_KEY).gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION).connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true).buildClient();
        AzureUsersWithNoSQL.db = AzureUsersWithNoSQL.cosmosClient.getDatabase(DB_NAME);
        AzureUsersWithNoSQL.usersDB = db.getContainer(DB_COLLECTION);
        Log.info("Created connection with DB");
        this.cache = AzureCache.getInstance();
    }

    @Override
    public Result<String> createUser(User user) {
        Log.info(() -> format("createUser : %s\n", user));

        if (badUserInfo(user))
            return error(BAD_REQUEST);

        try {
            Result<User> userAlreadyExists = this.getUser(user.getUserId(), user.getPwd());
            if (userAlreadyExists.isOK()) return error(ErrorCode.CONFLICT);
            if (userAlreadyExists.error().equals(ErrorCode.NOT_FOUND)) {
                user.setUserId(user.getUserId());
                CosmosItemResponse<User> response = usersDB.createItem(user);
                if (response.getStatusCode() < 300) {
                    if (useCache) {
                        setUserCache(userAlreadyExists.value());
                        deleteSearchCache("");
                    }
                    return ok(response.getItem().getUserId());
                } else {
                    return error(BAD_REQUEST);
                }
            }
            return error(userAlreadyExists.error());
        } catch (Exception e) {
            return error(ErrorCode.INTERNAL_ERROR);
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

            String searchPattern = (pattern != null) ? pattern.toUpperCase() : "";
            var query = format("SELECT * FROM users u WHERE u.userId LIKE '%%%s%%'", searchPattern);
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

            List<User> users = usersDB.queryItems(query, options, User.class).stream().map(User::copyWithoutPassword)
                    .collect(Collectors.toList());
            if (useCache) {
                setSearchCache(pattern, users);
            }
            return ok(users);
        } catch (Exception e) {
            Log.info(e.getMessage());
            return error(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<User> updateUser(String userId, String pwd, User other) {
        Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

        if (badUpdateUserInfo(userId, pwd, other))
            return error(BAD_REQUEST);

        return errorOrResult(
                validatedUserOrError(this.getUser(userId, pwd), pwd),
                user -> {
                    User userUpdated = user.updateFrom(other);
                    usersDB.replaceItem(userUpdated, userUpdated.getId(), new PartitionKey(userId),
                            new CosmosItemRequestOptions());
                    if (useCache) {
                        setUserCache(userUpdated);
                        deleteSearchCache("");
                    }
                    return ok(userUpdated);
                });
    }

    @Override
    public Result<User> deleteUser(String userId, String pwd) {
        Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

        return errorOrResult(validatedUserOrError(this.getUser(userId, pwd), pwd), user -> {
            if (useCache) {
                deleteUserCache(user);
                deleteSearchCache("");
            }
            AzureShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
            AzureBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
            usersDB.deleteItem(user.getId(), new PartitionKey(userId), new CosmosItemRequestOptions());
            return ok();
        });
    }

    @Override
    public Result<User> getUser(String userId, String pwd) {
        Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

        if (userId == null)
            return error(BAD_REQUEST);

        if (useCache) {
            User cachedUser = getUserFromCache(userId);
            if (cachedUser != null) {
                Log.info(() -> format("user cache hit: userId = %s\n", userId));
                return ok(cachedUser);
            }
            Log.info(() -> format("user cache miss: userId = %s\n", userId));
        }
        try {
            Optional<User> user = usersDB
                    .queryItems(format("SELECT * FROM User u WHERE u.userId = '%s'", userId),
                            new CosmosQueryRequestOptions(), User.class)
                    .stream().findFirst();

            return user.map(Result::ok).orElse(error(ErrorCode.NOT_FOUND));
        } catch (Exception e) {
            return error(Result.ErrorCode.INTERNAL_ERROR);
        }
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
     * Validates a requested user across all fields
     *
     * @param user requested
     * @return true if bad request; false otherwise
     */
    private boolean badUserInfo(User user) {
        return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
    }

    /**
     * Validates user password
     *
     * @param res the user received
     * @param pwd the password suggested
     * @return user if validated, error otherwise
     */
    private Result<User> validatedUserOrError(Result<User> res, String pwd) {
        if (res.isOK())
            return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
        else
            return res;
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
     *
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