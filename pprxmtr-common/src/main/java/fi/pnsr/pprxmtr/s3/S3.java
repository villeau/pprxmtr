package fi.pnsr.pprxmtr.s3;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

public class S3 {

	private static final Logger LOG = LogManager.getLogger();

	private static final Properties properties = new Properties();

	private static final AmazonS3 S3 = AmazonS3ClientBuilder.defaultClient();

	private static final String S3_BUCKET_NAME = "s3.bucket.name";

	static {
		URL url = Resources.getResource("application.properties");
		final ByteSource byteSource = Resources.asByteSource(url);
		try (InputStream inputStream = byteSource.openBufferedStream()) {
			properties.load(inputStream);
		} catch (IOException e) {
			LOG.error("openBufferedStream failed!", e);
		}
	}

	public static boolean fileExistsInBucket(String filenamePrefix) {
		try {
			ObjectListing ol = S3.listObjects(properties.getProperty(S3_BUCKET_NAME));
			List<S3ObjectSummary> objects = ol.getObjectSummaries();
			for (S3ObjectSummary os : objects) {
				if (StringUtils.startsWith(os.getKey(), filenamePrefix)) {
					LOG.info("Found file {} from S3 bucket.", filenamePrefix);
					return true;
				}
			}
		} catch (AmazonServiceException e) {
			LOG.error("Exception occured when checking if file exists in S3.", e);
		}
		return false;
	}

	public static String getS3KeyFromBucket(String filenamePrefix) {
		try {
			ObjectListing ol = S3.listObjects(properties.getProperty(S3_BUCKET_NAME));
			List<S3ObjectSummary> objects = ol.getObjectSummaries();
			for (S3ObjectSummary os : objects) {
				if (StringUtils.startsWith(os.getKey(), filenamePrefix)) {
					LOG.info("Found file {} from S3 bucket.", filenamePrefix);
					return os.getKey();
				}
			}
		} catch (AmazonServiceException e) {
			LOG.error("Exception occured when checking if file exists in S3.", e);
		}
		return StringUtils.EMPTY;
	}

	public static void storeFileInBucket(String filename, InputStream is, ObjectMetadata metadata) {
		String bucketName = properties.getProperty(S3_BUCKET_NAME);
		try {
			LOG.info("Storing file {} in S3 bucket {}.", filename, bucketName);
			S3.putObject(bucketName, filename, is, metadata);
			S3.setObjectAcl(bucketName, filename, CannedAccessControlList.PublicRead);
		} catch (AmazonServiceException e) {
			LOG.error("Exception occured when fetching file from S3 bucket {}.", bucketName, e);
		}
	}

}
