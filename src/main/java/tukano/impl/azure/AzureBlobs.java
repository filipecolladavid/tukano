package tukano.impl.azure;

import static java.lang.String.format;
import static tukano.api.Result.ok;
import static tukano.api.Result.error;

import java.util.function.Consumer;
import java.util.logging.Logger;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import static tukano.api.Result.ErrorCode.NOT_FOUND;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;

import tukano.api.Blobs;
import tukano.api.Result;
import utils.Hash;
import utils.Hex;

public class AzureBlobs implements Blobs {
    private static Blobs instance;
    private static Logger Log = Logger.getLogger(AzureBlobs.class.getName());

    public String baseURI;

    private final AzureCache cache;

    private String storageConnectionString;
    private String containerName;
    private String storageAccount;
    private BlobContainerClient containerClient;

    private static final String BLOB_CACHE_KEY = "blobs:";
    private static final int MAX_CACHE_SIZE = 1024 * 1024;
    private static final int CACHE_EXPIRY = 3600; // 1 hour in seconds

    private AzureBlobs() {
        this.storageConnectionString = System.getProperty("BLOB_STORE_CONNECTION");
        this.containerName = System.getProperty("CONTAINER_NAME");
        this.containerClient = new BlobContainerClientBuilder().connectionString(storageConnectionString)
                .containerName(containerName).buildClient();
        this.storageAccount = System.getProperty("STORAGE_ACCOUNT");
        this.baseURI = String.format("https://%s.blob.core.windows.net/%s/", storageAccount, containerName);

        this.cache = AzureCache.getInstance();
    }

    synchronized public static Blobs getInstance() {
        if (instance == null)
            instance = new AzureBlobs();
        return instance;
    }

    @Override
    public Result<Void> upload(String blobId, byte[] bytes, String token) {
        Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)),
                token));

        var blob = containerClient.getBlobClient(blobId);
        var data = BinaryData.fromBytes(bytes);
        blob.upload(data, true);

        setBlobCache(blobId, bytes);

        return ok();

    }

    @Override
    public Result<byte[]> download(String blobId, String token) {
        Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

        byte[] cachedData = getFromBlobCache(blobId);

        if (cachedData != null)
            return ok(cachedData);

        var blob = containerClient.getBlobClient(blobId);

        if (!blob.exists()) {
            return error(NOT_FOUND);
        }

        byte[] data = blob.downloadContent().toBytes();
        setBlobCache(blobId, data);

        return ok(data);
    }

    public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink, String token) {
        Log.info(() -> format("downloadToSink : blobId = %s, token = %s\n", blobId, token));

        byte[] cachedData = getFromBlobCache(blobId);
        if (cachedData != null) {
            sink.accept(cachedData);
            return ok();
        }

        var blob = containerClient.getBlobClient(blobId);

        if (!blob.exists()) {
            return error(NOT_FOUND);
        }

        byte[] data = blob.downloadContent().toBytes();
        setBlobCache(blobId, data);

        sink.accept(data);
        return ok();
    }

    @Override
    public Result<Void> delete(String blobId, String token) {
        Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

        containerClient.getBlobClient(blobId).deleteIfExists();

        deleteBlobCache(blobId);

        return ok();
    }

    @Override
    public Result<Void> deleteAllBlobs(String userId, String token) {
        Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId,
                token));

        for (BlobItem blob : containerClient.listBlobs()) {
            String blobName = blob.getName();

            if (blobName.startsWith(userId)) {
                containerClient.getBlobClient(blobName).deleteIfExists();

                deleteBlobCache(blobName);
            }
        }

        return ok();
    }

    private String getBlobCacheKey(String blobId) {
        return BLOB_CACHE_KEY + blobId;
    };

    private void setBlobCache(String blobId, byte[] data) {
        if (data != null && data.length <= MAX_CACHE_SIZE) {
            Log.info(() -> format("caching blob: blobId = %s, size = %d bytes\n", blobId, data.length));
            cache.setWithExpiry(getBlobCacheKey(blobId), Hex.of(data), CACHE_EXPIRY);
        }
    };

    private void deleteBlobCache(String blobId) {
        Log.info(() -> format("deleting blob cache: blobId = %s\n", blobId));
        cache.delete(getBlobCacheKey(blobId));
    };

    private byte[] getFromBlobCache(String blobId) {
        var cachedHex = cache.get(getBlobCacheKey(blobId));
        if (cachedHex != null) {
            Log.info(() -> format("blob cache hit: blobId = %s\n", blobId));
            return Hex.toBytes(cachedHex);
        }
        Log.info(() -> format("blob cache miss: blobId = %s\n", blobId));
        return null;
    }
}
