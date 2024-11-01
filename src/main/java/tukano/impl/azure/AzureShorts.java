package tukano.impl.azure;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import java.util.ArrayList;
import java.util.List;
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

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.Token;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;

public class AzureShorts implements Shorts {
    private static Shorts instance;
    private static Logger Log = Logger.getLogger(AzureShorts.class.getName());

    private static String endpoint;
    private static String DB_KEY;

    private static String DB_NAME;
    private static String SHORTS_DB_COLLECTION;
    private static String LIKES_DB_COLLECTION;
    private static String FOLLOWERS_DB_COLLECTION;

    private static CosmosClient cosmosClient;
    private static CosmosDatabase db;
    private static CosmosContainer shortsDB;
    private static CosmosContainer likesDB;
    private static CosmosContainer followersDB;

    synchronized public static Shorts getInstance() {
        if (instance == null)
            instance = new AzureShorts();

        return instance;
    }

    private AzureShorts() {

        AzureShorts.endpoint = System.getProperty("COSMOSDB_URL");
        AzureShorts.DB_KEY = System.getProperty("COSMOSDB_KEY");
        AzureShorts.DB_NAME = System.getProperty("COSMOSDB_NAME");
        AzureShorts.SHORTS_DB_COLLECTION = "shorts";
        AzureShorts.LIKES_DB_COLLECTION = "likes";
        AzureShorts.FOLLOWERS_DB_COLLECTION = "followers";

        AzureShorts.cosmosClient = new CosmosClientBuilder().endpoint(endpoint).key(DB_KEY).gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION).connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true).buildClient();

        AzureShorts.db = AzureShorts.cosmosClient.getDatabase(DB_NAME);
        AzureShorts.shortsDB = db.getContainer(SHORTS_DB_COLLECTION);
        AzureShorts.likesDB = db.getContainer(LIKES_DB_COLLECTION);
        AzureShorts.followersDB = db.getContainer(FOLLOWERS_DB_COLLECTION);
    }

    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

        return errorOrResult(okUser(userId, password), user -> {
            try {
                var shortId = format("%s+%s", userId, UUID.randomUUID());
                var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
                var shrt = new Short(shortId, userId, blobUrl);

                CosmosItemResponse<Short> response = shortsDB.createItem(shrt);
                if (response.getStatusCode() < 300) {
                    return ok(response.getItem().copyWithLikes_And_Token(0));
                } else {
                    return error(BAD_REQUEST);
                }

            } catch (Exception e) {
                return error(BAD_REQUEST);
            }
        });
    }

    @Override
    public Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if (shortId == null)
            return error(BAD_REQUEST);

        try {
            CosmosItemResponse<Short> readItem = shortsDB.readItem(shortId, new PartitionKey(shortId), Short.class);
            Short currentShort = readItem.getItem();

            if (currentShort == null) {
                CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
                options.setConsistencyLevel(ConsistencyLevel.STRONG);
                var query = format("SELECT * FROM Short s WHERE s.shortId = '%s'", shortId);
                currentShort = shortsDB.queryItems(query, options, Short.class)
                        .stream().findFirst().orElse(null);
            }
            if (currentShort == null) {
                Log.warning(() -> format("Short not found: %s\n", shortId));
                return error(NOT_FOUND);
            }

            return ok(currentShort);

        } catch (Exception e) {
            Log.warning(() -> format("Error in getShort: %s\n", e.getMessage()));
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> deleteShort(String shortId, String password) {
        return ok();
    }

    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));
        if (userId == null)
            return error(BAD_REQUEST);

        try {
            var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

            List<String> shortsID = shortsDB.queryItems(query, options,
                    Short.class).stream().map(key -> key.getShortId()).collect(Collectors.toList());

            return ok(shortsID);

        } catch (Exception e) {
            return error(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
                isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            if (isFollowing) {
                followersDB.createItem(f);
            } else {
                PartitionKey partitionKey = new PartitionKey(f.getFollowee());
                followersDB.deleteItem(f.getId(), partitionKey, new CosmosItemRequestOptions());
            }
            return ok();
        });
    }

    @Override
    public Result<List<String>> followers(String userId, String password) {
        Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

        var query = format("SELECT f.follower FROM Followers f WHERE f.followee = '%s'", userId);
        return errorOrValue(okUser(userId, password),
                followersDB.queryItems(query, new CosmosQueryRequestOptions(),
                        Following.class).stream().map(key -> key.getFollower()).collect(Collectors.toList()));
    }

    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
                password));

        return errorOrResult(getShort(shortId), shrt -> {
            var l = new Likes(userId, shortId, shrt.getOwnerId());
            if (isLiked) {
                likesDB.createItem(l);
            } else {
                PartitionKey partitionKey = new PartitionKey(l.getShortId());
                likesDB.deleteItem(l.getId(), partitionKey, new CosmosItemRequestOptions());
            }
            return ok();
        });
    }

    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));
        return errorOrResult(getShort(shortId), shrt -> {
            Log.info(() -> format("find likes for shortId = %s\n", shortId));
            var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

            List<String> usersID = likesDB.queryItems(query, options,
                    Likes.class).stream().map(key -> key.getUserId()).collect(Collectors.toList());

            return errorOrValue(okUser(shrt.getOwnerId(), password), usersID);
        });
    }

    @Override
    public Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));
        return errorOrValue(okUser(userId, password), user -> {
            try {
                var allFollowedQuery = format("SELECT f.followee FROM Following f WHERE f.follower = '%s'", userId);
                List<String> followedUsers = followersDB.queryItems(allFollowedQuery,
                                new CosmosQueryRequestOptions(),
                                Following.class)
                        .stream()
                        .map(Following::getFollowee)
                        .collect(Collectors.toList());

                followedUsers.add(userId);

                String userList = followedUsers.stream()
                        .map(id -> "'" + id + "'")
                        .collect(Collectors.joining(","));

                var followedShortsQuery = format(
                        "SELECT s.shortId FROM Short s WHERE s.ownerId IN (%s) ORDER BY s.timestamp DESC", userList);

                return shortsDB.queryItems(followedShortsQuery,
                                new CosmosQueryRequestOptions(),
                                Short.class)
                        .stream()
                        .map(Short::getShortId)
                        .collect(Collectors.toList());

            } catch (Exception e) {
                return new ArrayList<>();
            }
        });
    }

    @Override
    public Result<Void> deleteAllShorts(String userId, String password, String token) {
        Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

        if (!Token.isValid(token, userId)) {
            return error(FORBIDDEN);
        }

        try {
            var selectShortsQuery = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

            List<String> shortsID = shortsDB.queryItems(selectShortsQuery, options,
                    Short.class).stream().map(key -> key.getShortId()).collect(Collectors.toList());

            if (!shortsID.isEmpty()) {
                shortsID.forEach(s -> shortsDB.deleteItem(s, new PartitionKey(s), null));
            }

            List<Following> followers = followersDB
                    .queryItems(format("SELECT * FROM Following f WHERE f.followee = '%s'",
                                    userId),
                            new CosmosQueryRequestOptions(),
                            Following.class)
                    .stream().collect(Collectors.toList());

            if (!followers.isEmpty()) {
                followers.forEach(f -> followersDB.deleteItem(f.getId(), new PartitionKey(f.getFollowee()), null));
            }

            if (!shortsID.isEmpty()) {
                String shortsIdList = shortsID.stream()
                        .map(s -> "'" + s + "'")
                        .collect(Collectors.joining(","));

                var likesQuery = format("SELECT * FROM Likes l WHERE l.shortId IN (%s)",
                        shortsIdList);
                List<Likes> likes = likesDB.queryItems(likesQuery,
                                new CosmosQueryRequestOptions(),
                                Likes.class)
                        .stream()
                        .collect(Collectors.toList());

                if (!likes.isEmpty()) {
                    likes.forEach(l -> likesDB.deleteItem(l.getId(), new PartitionKey(l.getShortId()), null));
                }
            }

            return ok();
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    protected Result<User> okUser(String userId, String pwd) {
        return AzureUsersWithSQL.getInstance().getUser(userId, pwd);
    }
}