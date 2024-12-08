package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.ok;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;

public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
	private final Cache cache;
	private final Gson gson;
	private final Boolean useCache;

	private static final String USER_CACHE_KEY = "user:";
	private static final String SEARCH_CACHE_KEY = "search:";

	private static Users instance;

	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
		this.cache = Cache.getInstance();
		this.gson = new Gson();
		this.useCache = Boolean.valueOf(System.getenv("CACHE"));
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));
		if( badUserInfo( user ) )
			return error(BAD_REQUEST);
		try {
			Result<User> newUser = DB.insertOne(user);
			if(newUser.isOK()) {
				if(useCache) {
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
		if (userId == null)
			return error(BAD_REQUEST);

		if (useCache) {
			User cachedUser = getUserFromCache(userId);
			if (cachedUser != null) {
				Log.info(() -> format("Cache hit: getUser : userId = %s, pwd = %s\n", userId, pwd));
				return validatedUserOrError(Result.ok(cachedUser), pwd);
			}
		}

		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));
		return validatedUserOrError(DB.getOne(userId, User.class), pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
			Result<User> updatedUser = DB.updateOne(user.updateFrom(other));
			if(updatedUser.isOK() && useCache) {
				setUserCache(updatedUser.value());
				Log.info(() -> format("Cache updateUser : userId = %s, pwd = %s\n", userId, pwd));
			}
			return updatedUser;
		});
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
			if (useCache) {
				deleteSearchCache(userId);
				deleteUserCache(user);
			}

			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			return DB.deleteOne(user);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : pattern = %s\n", pattern));
		try {
			if (useCache) {
				List<User> cachedUsers = getFromSearchCache(pattern);
				if (cachedUsers != null) {
					Log.info(() -> format("search cache hit: pattern = %s\n", pattern));
					return ok(cachedUsers);
				}
				Log.info(() -> format("search cache miss: pattern = %s\n", pattern));
			}

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
				Log.info(() -> format("search cache updated: pattern = %s\n", pattern));
			}

			return ok(hits);

		} catch (Exception e) {
			Log.severe(() -> format("Error during searchUsers: pattern = %s, error = %s\n", pattern, e.getMessage()));
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
