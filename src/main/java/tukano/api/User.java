package tukano.api;

import java.util.UUID;

import org.hibernate.annotations.PartitionKey;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class User {

    @Id
    private String id;

    @PartitionKey
    private String userId;

    private String pwd;
    private String email;
    private String displayName;

    public User() {
    }

    public User(String userId, String pwd, String email, String displayName) {
        this.id = UUID.randomUUID().toString();
        this.pwd = pwd;
        this.email = email;
        this.userId = userId;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public void setId(String userId) {
        this.id = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String userId() {
        return userId;
    }

    public String pwd() {
        return pwd;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return "User [userId=" + userId + ", pwd=" + pwd + ", email=" + email + ", displayName=" + displayName + "]";
    }

    public User copyWithoutPassword() {
        var newUser = new User(userId, "", email, displayName);
        newUser.setId(id);
        return newUser;
    }

    public User updateFrom(User other) {
        var updatedUser = new User(userId,
                other.pwd != null ? other.pwd : pwd,
                other.email != null ? other.email : email,
                other.displayName != null ? other.displayName : displayName);

        updatedUser.setId(id);
        return updatedUser;
    }
}
