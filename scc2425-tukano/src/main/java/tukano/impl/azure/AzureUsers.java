package tukano.impl.azure;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
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

public class AzureUsers implements Users {
    private static Logger Log = Logger.getLogger(AzureUsers.class.getName());
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
            instance = new AzureUsers();
        }
        return instance;
    }

    private AzureUsers() {
        AzureUsers.endpoint = "https://scc70056.documents.azure.com/";
        AzureUsers.DB_KEY = "fx20nonbvOiEDpOIxY92Rfu1XrKIaAdoS9Oev88K75IKP3zm3JYqC7JQGpqrlnz7xny0SbQROlfDACDb0xNJoQ==";
        AzureUsers.DB_NAME = "scc70056";
        AzureUsers.DB_COLLECTION = "users";

        AzureUsers.cosmosClient = new CosmosClientBuilder().endpoint(endpoint).key(DB_KEY).gatewayMode()
                .consistencyLevel(ConsistencyLevel.SESSION).connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true).buildClient();

        AzureUsers.db = AzureUsers.cosmosClient.getDatabase(DB_NAME);
        AzureUsers.usersDB = db.getContainer(DB_COLLECTION);
    }

    @Override
    public Result<String> createUser(User user) {
        Log.info(() -> format("createUser : %s\n", user));

        if (badUserInfo(user))
            return error(BAD_REQUEST);

        try {

            User userAlreadyExists = this.getUser(user.getUserId(), user.getPwd()).value();

            if (userAlreadyExists != null)
                return error(ErrorCode.CONFLICT);

            CosmosItemResponse<User> response = usersDB.createItem(user);
            if (response.getStatusCode() < 300) {
                return ok(response.getItem().getUserId());
            } else {
                return error(BAD_REQUEST);
            }

        } catch (Exception e) {
            return error(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

        try {
            var query = format("SELECT * FROM users u WHERE u.userId LIKE '%%%s%%'", pattern.toUpperCase());
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

        try {
            User userToBeUpdated = this.getUser(userId, pwd).value();

            if (userToBeUpdated == null)
                return error(ErrorCode.NOT_FOUND);

            User userUpdated = userToBeUpdated.updateFrom(other);

            usersDB.replaceItem(userUpdated, userUpdated.getId(), new PartitionKey(userId),
                    new CosmosItemRequestOptions());

            return ok(userUpdated);
        } catch (Exception e) {
            return error(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<User> deleteUser(String userId, String pwd) {
        Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

        if (userId == null || pwd == null)
            return error(BAD_REQUEST);

        try {
            User userToBeDeleted = this.getUser(userId, pwd).value();

            if (userToBeDeleted == null)
                return error(ErrorCode.NOT_FOUND);

            CosmosItemResponse<Object> deletedResponse = usersDB.deleteItem(userToBeDeleted.getId(),
                    new PartitionKey(userToBeDeleted.getUserId()),
                    new CosmosItemRequestOptions());
            if (deletedResponse.getStatusCode() < 300) {
                System.out.println("Delete Status Code: " + deletedResponse.getStatusCode());
                return ok();

            } else
                return error(FORBIDDEN);
        } catch (Exception e) {
            return error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<User> getUser(String userId, String pwd) {
        Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

        if (userId == null)
            return error(BAD_REQUEST);
        try {

            User user = usersDB
                    .queryItems(format("SELECT * FROM User u WHERE u.userId = '%s'", userId),
                            new CosmosQueryRequestOptions(), User.class)
                    .stream().findFirst().get();

            if (user == null)
                return error(ErrorCode.NOT_FOUND);

            return ok(user);

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
}
