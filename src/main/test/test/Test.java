package test;

import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;

import tukano.api.Result;
import tukano.api.User;
import tukano.clients.rest.RestBlobsClient;
import tukano.clients.rest.RestShortsClient;
import tukano.clients.rest.RestUsersClient;
import tukano.impl.rest.TukanoRestServer;

public class Test {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }

    public static void printBanner(String message) {
        String borderLine = "-".repeat(91);

        System.out.println("\n" + borderLine);
        System.out.printf("%-45s %s %-45s%n", "", message, "");
        System.out.println(borderLine + "\n");
    }

    public static void main(String[] args) throws Exception {
        TukanoRestServer server = new TukanoRestServer();
        Thread.sleep(1000);

        var serverURILocal = String.format("http://localhost:%s/tukano/rest", TukanoRestServer.PORT);

        //TODO - change to your own url
        var serverURIAzure = "https://tukano-70056-70666-123.azurewebsites.net/rest";
        var serverURI = System.getProperty("ENV").equals("local") ? serverURILocal : serverURIAzure;
        System.out.println(serverURI);

        var blobs = new RestBlobsClient(serverURI);
        var users = new RestUsersClient(serverURI);
        var shorts = new RestShortsClient(serverURI);
        printBanner("CREATING USERS");
        show(users.createUser(new User("wales", "12345", "jimmy@wikipedia.pt", "Jimmy Wales")));
        show(users.createUser(new User("liskov", "54321", "liskov@mit.edu", "Barbara Liskov")));

        printBanner("UPDATE USERS");
        show(users.updateUser("wales", "12345", new User("wales", "12345", "jimmy@wikipedia.com", "")));

        printBanner("SEARCH USERS");
        show(users.searchUsers(""));

        Result<tukano.api.Short> s1, s2;

        printBanner("CREATE SHORTS");
        show(s2 = shorts.createShort("liskov", "54321"));
        show(s1 = shorts.createShort("wales", "12345"));
        show(shorts.createShort("wales", "12345"));
        show(shorts.createShort("wales", "12345"));
        show(shorts.createShort("wales", "12345"));

        printBanner("CREATE BLOBS");
        var blobUrl = URI.create(s2.value().getBlobUrl());
        System.out.println("------->" + blobUrl);

        var blobId = new File(blobUrl.getPath()).getName();
        System.out.println("BlobID:" + blobId);

        var token = blobUrl.getQuery().split("=")[1];

        blobs.upload(blobUrl.toString(), randomBytes(100), token);

        var s2id = s2.value().getShortId();


        printBanner("FOLLOW");
        show(shorts.follow("liskov", "wales", true, "54321"));
        show(shorts.followers("wales", "12345"));

        printBanner("LIKES");
        show(shorts.like(s2id, "liskov", true, "54321"));
        show(shorts.like(s2id, "liskov", true, "54321"));
        show(shorts.likes(s2id, "54321"));

        printBanner("GET FEED");
        show(shorts.getFeed("liskov", "12345"));

        printBanner("GET SHORT(s)");
        show(shorts.getShort(s2id));;
        show(shorts.getShorts("wales"));


        printBanner("GET FOLLOWERS");
        show(shorts.followers("wales", "12345"));
        printBanner("GET FEED");
        show(shorts.getFeed("liskov", "12345"));
        printBanner("GET SHORT");
        show(shorts.getShort(s2id));
        //
        //
        // blobs.forEach( b -> {
        // var r = b.download(blobId);
        // System.out.println( Hex.of(Hash.sha256( bytes )) + "-->" +
        // Hex.of(Hash.sha256( r.value() )));
        //
        // });

//        printBanner("DELETE USERS");
//        show(users.deleteUser("wales", "12345"));
//        show(users.deleteUser("liskov", "54321"));

        System.exit(0);
    }

    private static Result<?> show(Result<?> res) {
        if (res.isOK())
            System.err.println("OK: " + res.value());
        else
            System.err.println("ERROR:" + res.error());
        return res;

    }

    private static byte[] randomBytes(int size) {
        var r = new Random(1L);

        var bb = ByteBuffer.allocate(size);

        r.ints(size).forEach(i -> bb.put((byte) (i & 0xFF)));

        return bb.array();

    }
}
