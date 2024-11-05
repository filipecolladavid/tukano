package tukano.impl.rest;

import java.util.List;
import java.util.logging.Logger;

import jakarta.inject.Singleton;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.rest.RestShorts;
import tukano.impl.JavaShorts;
import tukano.impl.azure.AzureCache;
import tukano.impl.azure.AzureShorts;

import static java.lang.String.format;

@Singleton
public class RestShortsResource extends RestResource implements RestShorts {
	private static Logger Log = Logger.getLogger(RestShortsResource.class.getName());

	static final Shorts impl = AzureShorts.getInstance();
		
	@Override
	public Short createShort(String userId, String password) {
		return super.resultOrThrow( impl.createShort(userId, password));
	}

	@Override
	public void deleteShort(String shortId, String password) {
		super.resultOrThrow( impl.deleteShort(shortId, password));
	}

	@Override
	public Short getShort(String shortId) {
		return super.resultOrThrow( impl.getShort(shortId));
	}
	@Override
	public List<String> getShorts(String userId) {
		return super.resultOrThrow( impl.getShorts(userId));
	}

	@Override
	public void follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));
		super.resultOrThrow( impl.follow(userId1, userId2, isFollowing, password));
	}

	@Override
	public List<String> followers(String userId, String password) {
		return super.resultOrThrow( impl.followers(userId, password));
	}

	@Override
	public void like(String shortId, String userId, boolean isLiked, String password) {
		super.resultOrThrow( impl.like(shortId, userId, isLiked, password));
	}

	@Override
	public List<String> likes(String shortId, String password) {
		return super.resultOrThrow( impl.likes(shortId, password));
	}

	@Override
	public List<String> getFeed(String userId, String password) {
		return super.resultOrThrow( impl.getFeed(userId, password));
	}

	@Override
	public void deleteAllShorts(String userId, String password, String token) {
		super.resultOrThrow( impl.deleteAllShorts(userId, password, token));
	}	
}
