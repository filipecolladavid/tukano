package tukano.impl.azure;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
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

    synchronized public static Users getInstance() {
        if (instance == null)
            instance = new AzureUsersWithSQL();
        return instance;
    }

    private AzureUsersWithSQL() {
        this.cache = AzureCache.getInstance();
        this.gson = new Gson();
    }

    @Override
    public Result<String> createUser(User user) {
        Log.info(() -> format("createUser : %s\n", user));
        if (badUserInfo(user)) {
            return error(BAD_REQUEST);
        }
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }
        try {
            var newUser = DB.insertOne(user);
            if (newUser.isOK()) {
                setUserCache(newUser.value());
                deleteSearchCache("");
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
        if (userId == null)
            return error(BAD_REQUEST);

        User cachedUser = getFromUserCache(userId);
        if (cachedUser != null) {
            Log.info(() -> format("user cache hit: userId = %s\n", userId));
            return validatedUserOrError(ok(cachedUser), pwd); // Validate password for cached user
        }

        Log.info(() -> format("user cache miss: userId = %s\n", userId));
        Result<User> user = DB.getOne(userId, User.class);
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
            Result<User> user = validatedUserOrError(DB.getOne(userId, User.class), pwd);
            if (!user.isOK())
                return error(FORBIDDEN);

            User updatedUser = user.value().updateFrom(other);
            Result<User> result = DB.updateOne(updatedUser);

            if (result.isOK()) {
                setUserCache(result.value());
                deleteSearchCache("");
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
        if (userId == null || pwd == null)
            return error(BAD_REQUEST);
        return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
            AzureShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
            AzureBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
            Result<User> result = DB.deleteOne(user);

            if (result.isOK()) {
                deleteUserCache(user);
                deleteSearchCache("");
            }
            return ok(user);
        });
    }

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        Log.info(() -> format("searchUsers : patterns = %s\n", pattern));
        try {
            List<User> cachedUsers = getFromSearchCache(pattern);
            if (cachedUsers != null) {
                Log.info(() -> format("search cache hit: pattern = %s\n", pattern));
                return ok(cachedUsers);
            }

            Log.info(() -> format("search cache miss: pattern = %s\n", pattern));
            String query = "";
            if (pattern == null || pattern.isEmpty()) {
                query = "SELECT * FROM \"users\" u";
            } else {
                query = format("SELECT * FROM \"users\" u WHERE UPPER(u.\"userId\") LIKE '%%%s%%'",
                        pattern.toUpperCase());
            }
            var hits = DB.sql(query, User.class)
                    .stream()
                    .map(User::copyWithoutPassword)
                    .toList();
            setSearchCache(pattern, hits);
            return ok(hits);
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    private Result<User> validatedUserOrError(Result<User> res, String pwd) {
        if (res.isOK())
            return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
        else
            return res;
    }

    private boolean badUserInfo(User user) {
        return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
    }

    private boolean badUpdateUserInfo(String userId, String pwd, User info) {
        return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
    }

    private String getUserCacheKey(String userId) {
        return USER_CACHE_KEY + userId;
    };

    private String getSearchCacheKey(String pattern) {
        return SEARCH_CACHE_KEY + pattern;
    };

    private void deleteUserCache(User user) {
        Log.info(() -> format("deleteUserCache: userId = %s\n", user.getUserId()));
        if (user != null)
            cache.delete(getUserCacheKey(user.getUserId()));
    };

    private void deleteSearchCache(String pattern) {
        Log.info(() -> format("deleteSearchCache: pattern = %s\n", pattern));
        if (pattern != null)
            cache.delete(getSearchCacheKey(pattern));
    };

    private void setUserCache(User user) {
        Log.info(() -> format("setUserCache: userId = %s\n", user.getUserId()));
        if (user != null)
            cache.set(getUserCacheKey(user.getUserId()), gson.toJson(user));
    };

    private void setSearchCache(String pattern, List<User> users) {
        Log.info(() -> format("setSearchCache: pattern = %s\n", pattern));
        if (pattern != null)
            cache.set(getSearchCacheKey(pattern), gson.toJson(users));
    };

    private User getFromUserCache(String userId) {
        var res = cache.get(getUserCacheKey(userId));
        if (res != null)
            return gson.fromJson(res, User.class);
        else
            return null;
    }

    private List<User> getFromSearchCache(String pattern) {
        var res = cache.get(getSearchCacheKey(pattern));
        if (res != null)
            return gson.fromJson(res, new TypeToken<List<User>>() {
            }.getType());
        else
            return null;
    }
}