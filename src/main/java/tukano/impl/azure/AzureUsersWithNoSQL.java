package tukano.impl.azure;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.Optional;
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

import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.Token;

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

    synchronized public static Users getInstance() {
        if (instance == null) {
            instance = new AzureUsersWithNoSQL();
        }
        return instance;
    }

    private AzureUsersWithNoSQL() {
        AzureUsersWithNoSQL.endpoint = System.getProperty("COSMOSDB_URL");
        AzureUsersWithNoSQL.DB_KEY = System.getProperty("COSMOSDB_KEY");
        AzureUsersWithNoSQL.DB_NAME = System.getProperty("COSMOSDB_NAME");
        AzureUsersWithNoSQL.DB_COLLECTION = "users";

        AzureUsersWithNoSQL.cosmosClient = new CosmosClientBuilder().endpoint(endpoint).key(DB_KEY).gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION).connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true).buildClient();

        AzureUsersWithNoSQL.db = AzureUsersWithNoSQL.cosmosClient.getDatabase(DB_NAME);
        AzureUsersWithNoSQL.usersDB = db.getContainer(DB_COLLECTION);
    }

    @Override
    public Result<String> createUser(User user) {
        Log.info(() -> format("createUser : %s\n", user));

        if (badUserInfo(user))
            return error(BAD_REQUEST);
        try {
            var userAlreadyExists = this.getUser(user.getUserId(), user.getPwd());
            if (userAlreadyExists.isOK())
                return error(ErrorCode.CONFLICT);

            if (userAlreadyExists.error() == ErrorCode.NOT_FOUND) {

                user.setId(user.getUserId());
                CosmosItemResponse<User> response = usersDB.createItem(user);
                if (response.getStatusCode() < 300) {
                    return ok(response.getItem().getUserId());
                } else {
                    return error(BAD_REQUEST);
                }
            }
            return error(userAlreadyExists.error());

        } catch (Exception e) {
            return error(ErrorCode.INTERNAL_ERROR);
        }

    };

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

        try {
            String searchPattern = (pattern != null) ? pattern.toUpperCase() : "";
            var query = format("SELECT * FROM users u WHERE u.userId LIKE '%%%s%%'", searchPattern);
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

            List<User> users = usersDB.queryItems(query, options, User.class).stream().map(User::copyWithoutPassword)
                    .collect(Collectors.toList());

            return ok(users);
        } catch (Exception e) {
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
                    return ok(userUpdated);
                });
    }

    @Override
    public Result<User> deleteUser(String userId, String pwd) {
        Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

        return errorOrResult(validatedUserOrError(this.getUser(userId, pwd), pwd), user -> {
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

        try {
            Optional<User> user = usersDB
                    .queryItems(format("SELECT * FROM User u WHERE u.userId = '%s'", userId),
                            new CosmosQueryRequestOptions(), User.class)
                    .stream().findFirst();

            return user.map(u -> ok(u)).orElse(error(ErrorCode.NOT_FOUND));
        } catch (Exception e) {
            return error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private boolean badUpdateUserInfo(String userId, String pwd, User info) {
        return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
    }

    private boolean badUserInfo(User user) {
        return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
    }

    private Result<User> validatedUserOrError(Result<User> res, String pwd) {
        if (res.isOK())
            return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
        else
            return res;
    }
}