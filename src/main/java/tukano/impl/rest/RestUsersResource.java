package tukano.impl.rest;

import java.util.List;

import jakarta.inject.Singleton;
import tukano.api.User;
import tukano.api.Users;
import tukano.api.rest.RestUsers;
import tukano.impl.azure.AzureUsersWithNoSQL;
import tukano.impl.azure.AzureUsersWithSQL;

@Singleton
public class RestUsersResource extends RestResource implements RestUsers {

    final Users impl;

    public RestUsersResource() {
        if(System.getProperty("USER_DB_TYPE").equals("NOSQL")) {
            this.impl = AzureUsersWithNoSQL.getInstance();
        } else {
            this.impl = AzureUsersWithSQL.getInstance();
        }
    }

    @Override
    public String createUser(User user) {
        return super.resultOrThrow(impl.createUser(user));
    }

    @Override
    public User getUser(String name, String pwd) {
        return super.resultOrThrow(impl.getUser(name, pwd));
    }

    @Override
    public User updateUser(String name, String pwd, User user) {
        return super.resultOrThrow(impl.updateUser(name, pwd, user));
    }

    @Override
    public User deleteUser(String name, String pwd) {
        return super.resultOrThrow(impl.deleteUser(name, pwd));
    }

    @Override
    public List<User> searchUsers(String pattern) {
        return super.resultOrThrow(impl.searchUsers(pattern));
    }
}
