package fi.pnsr.pprxmtr.sns;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

public class SNS {

	private static final String ARN_TEMPLATE = "arn:aws:sns:%s:%s:%s";

	private static final Logger LOG = LogManager.getLogger();

	private static final Properties properties = new Properties();

	private static AmazonSNSAsync SNS_CLIENT;

	static {
		URL url = Resources.getResource("application.properties");
		final ByteSource byteSource = Resources.asByteSource(url);
		try (InputStream inputStream = byteSource.openBufferedStream()) {
			properties.load(inputStream);
			SNS_CLIENT = AmazonSNSAsyncClientBuilder.standard()
					.withRegion(Regions.fromName(properties.getProperty("aws.region"))).build();
		} catch (IOException e) {
			LOG.error("openBufferedStream failed!", e);
		}
	}

	public static Future<PublishResult> publish(String topic, String message) {
		String arn = String.format(ARN_TEMPLATE, properties.getProperty("aws.region"),
				properties.getProperty("aws.user.id"), topic);
		LOG.info("Sending SNS publish request to topic: {}", arn);
		PublishRequest publishRequest = new PublishRequest(arn, message);
		return SNS_CLIENT.publishAsync(publishRequest);
	}

}
