package tukano.api;

import java.util.UUID;
import org.hibernate.annotations.PartitionKey;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "\"users\"")
public class User {
	@Id
	@Column(name = "\"id\"")
	private String id;

	@PartitionKey
	@Column(name = "\"userId\"", unique = true)
	private String userId;

	@Column(name = "\"pwd\"")
	private String pwd;

	@Column(name = "\"email\"")
	private String email;

	@Column(name = "\"displayName\"")
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
		this.id = userId != null ? userId : UUID.randomUUID().toString();
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
		return "User [id=" + id + ", userId=" + userId + ", pwd=" + pwd + ", email=" + email + ", displayName="
				+ displayName + "]";
	}

	public User copyWithoutPassword() {
		var newUser = new User(userId, "", email, displayName);
		newUser.setId(id);
		return newUser;
	}

	public User updateFrom(User other) {
		System.out.println(other);
		var updatedUser = new User(userId,
				other.pwd != null ? other.pwd : pwd,
				other.email != null ? other.email : email,
				other.displayName != null ? other.displayName : displayName);
		updatedUser.setId(id);
		System.out.println("UPDATED USER"+updatedUser);
		return updatedUser;
	}
}
