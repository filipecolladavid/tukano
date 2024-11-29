package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;

import java.io.ByteArrayInputStream;
import java.util.logging.Logger;

import io.minio.*;
import io.minio.messages.Item;
import tukano.api.Blobs;
import tukano.api.Result;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {

	private static Blobs instance;
	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());
	private MinioClient minioClient;
	private static final String BUCKET_NAME = "shorts";

	synchronized public static Blobs getInstance() {
		if (instance == null)
			instance = new JavaBlobs();
		return instance;
	}

	private JavaBlobs() {
		String minioHost = System.getenv("MINIO_URL");
		String minioAccessKey = System.getenv("MINIO_ACCESS_KEY");
		String minioSecretKey = System.getenv("MINIO_SECRET_KEY");
		minioClient = MinioClient.builder()
				.endpoint(minioHost)
				.credentials(minioAccessKey, minioSecretKey)
				.build();

		try {
			boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
			if (!exists) {
				minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
			}

			String policy = String.format(
					"{\"Version\":\"2012-10-17\"," +
							"\"Statement\":[{" +
							"\"Effect\":\"Allow\"," +
							"\"Principal\":{\"AWS\":[\"*\"]}," +
							"\"Action\":[\"s3:GetObject\"]," +
							"\"Resource\":[\"arn:aws:s3:::%s/*\"]" +
							"}]}", BUCKET_NAME);

			minioClient.setBucketPolicy(
					SetBucketPolicyArgs.builder()
							.bucket(BUCKET_NAME)
							.config(policy)
							.build()
			);

			Log.info("Successfully configured MinIO bucket: " + BUCKET_NAME);
		} catch (Exception e) {
			Log.severe("Error configuring MinIO: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n",
				blobId, Hex.of(Hash.sha256(bytes)), token));

		try {
			minioClient.putObject(
					PutObjectArgs.builder()
							.bucket(BUCKET_NAME)
							.object(toPath(blobId))
							.stream(new ByteArrayInputStream(bytes), bytes.length, -1)
							.build()
			);
			return ok();
		} catch (Exception e) {
			Log.severe("Error uploading to MinIO: " + e.getMessage());
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		try {
			GetObjectResponse response = minioClient.getObject(
					GetObjectArgs.builder()
							.bucket(BUCKET_NAME)
							.object(toPath(blobId))
							.build()
			);

			return ok(response.readAllBytes());
		} catch (Exception e) {
			if (e.getMessage().contains("NoSuchKey")) {
				return error(NOT_FOUND);
			}
			Log.severe("Error downloading from MinIO: " + e.getMessage());
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

		try {
			minioClient.removeObject(
					RemoveObjectArgs.builder()
							.bucket(BUCKET_NAME)
							.object(toPath(blobId))
							.build()
			);
			return ok();
		} catch (Exception e) {
			Log.severe("Error deleting from MinIO: " + e.getMessage());
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));
		try {
			Iterable<io.minio.Result<Item>> results = minioClient.listObjects(
					ListObjectsArgs.builder()
							.bucket(BUCKET_NAME)
							.prefix(userId)
							.build()
			);

			for (io.minio.Result<Item> result : results) {
				Item item = result.get();
				minioClient.removeObject(
						RemoveObjectArgs.builder()
								.bucket(BUCKET_NAME)
								.object(item.objectName())
								.build()
				);
			}
			return ok();
		} catch (Exception e) {
			Log.severe("Error deleting all blobs from MinIO: " + e.getMessage());
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
}