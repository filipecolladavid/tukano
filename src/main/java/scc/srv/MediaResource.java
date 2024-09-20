package scc.srv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import scc.utils.Hash;

/**
 * Resource for managing media files, such as images.
 */
@Path("/media")
public class MediaResource {
	Map<String, byte[]> map = new HashMap<>();

	/**
	 * Post a new image.The id of the image is its hash.
	 */
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	@Produces(MediaType.APPLICATION_JSON)
	public String upload(byte[] contents) {
		var key = Hash.of(contents);
		map.put(key, contents);
		return key;
	}

	/**
	 * Return the contents of an image. Throw an appropriate error message if id
	 * does not exist.
	 */
	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public byte[] download(@PathParam("id") String id) {
		var data = map.get( id );
		if( data == null )
			throw new WebApplicationException( Status.NOT_FOUND );
		return data;
	}

	/**
	 * Lists the ids of images stored.
	 */
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> list() {
		return new ArrayList<String>(map.keySet());
	}
}
