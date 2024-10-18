package tukano.impl.azure;

import static java.lang.String.format;
import static tukano.api.Result.ok;

import java.util.logging.Logger;

import java.util.function.Consumer;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
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

    private String storageConnectionString;
    private String containerName;
    private BlobContainerClient containerClient;

    private AzureBlobs() {
        this.storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc70056;AccountKey=kgk69wBGUOhxvafNpqniO6eaON4nT07gSlH798giINNxBaDWhDqpuAHUjrSM8AyvhumH7xZxCK8Q+ASt19dFHg==;EndpointSuffix=core.windows.net";
        this.containerName = "random";
        this.containerClient = new BlobContainerClientBuilder().connectionString(storageConnectionString)
                .containerName(containerName).buildClient();

        this.baseURI = String.format("https://%s.blob.core.windows.net/%s/", "scc70056", containerName);
    }

    synchronized public static Blobs getInstance() {
        if (instance == null)
            instance = new AzureBlobs();
        return instance;
    }

    @Override
    public Result<Void> upload(String blobId, byte[] bytes, String token) {
        var blob = containerClient.getBlobClient(blobId);
        var data = BinaryData.fromBytes(bytes);

        blob.upload(data, true);

        Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)),
                token));

        return ok();

    }

    @Override
    public Result<byte[]> download(String blobId, String token) {
        Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

        var blob = containerClient.getBlobClient(blobId);
        var data = blob.downloadContent().toBytes();
        return ok(data);
    }

    @Override
    public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink, String token) {
        Log.info(() -> format("downloadToSink : blobId = %s, token = %s\n", blobId, token));

        var blob = containerClient.getBlobClient(blobId);
        byte[] data = blob.downloadContent().toBytes();
        sink.accept(data);

        return ok();
    }

    @Override
    public Result<Void> delete(String blobId, String token) {
        Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

        containerClient.getBlobClient(blobId).deleteIfExists();

        return ok();
    }

    @Override
    public Result<Void> deleteAllBlobs(String userId, String token) {
        Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId,
                token));

        containerClient.listBlobs().forEach(blob -> System.out.printf("Name: %s%n",
                blob.getName()));

        for (BlobItem blob : containerClient.listBlobs()) {
            String blobName = blob.getName();

            if (blobName.startsWith(userId + "/")) {
                containerClient.getBlobClient(blobName).deleteIfExists();
            }
        }

        return ok();
    }
}
