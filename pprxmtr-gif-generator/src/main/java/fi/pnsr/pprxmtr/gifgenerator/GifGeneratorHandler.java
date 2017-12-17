package fi.pnsr.pprxmtr.gifgenerator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;

import fi.pnsr.pprxmtr.s3.S3;
import fi.pnsr.pprxmtr.sns.ApiGatewayResponse;
import fi.pnsr.pprxmtr.sns.Response;
import fi.pnsr.pprxmtr.sns.SNS;

public class GifGeneratorHandler implements RequestHandler<SNSEvent, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger();

	@Override
	public ApiGatewayResponse handleRequest(SNSEvent input, Context context) {
		LOG.info("Loading Gif Generator Java Lambda handler.");

		ObjectMapper mapper = new ObjectMapper();

		if (CollectionUtils.isNotEmpty(input.getRecords())) {
			try {
				JsonNode json = mapper.readTree(input.getRecords().get(0).getSNS().getMessage());

				byte[] gif = null;
				if (json.has("image")) {
					String base64 = json.get("image").asText();

					if (StringUtils.isNotBlank(base64)) {
						LOG.info("Found image {} in message body.", base64);
						byte[] imageByte = Base64.decodeBase64(base64);
						gif = GifGenerator.generateGif(imageByte);
					}
				}

				if (ArrayUtils.isNotEmpty(gif)) {
					LOG.info("Gif created successfully, storing in S3.");
					String emojiName = StringUtils.strip(json.get("text").asText());

					InputStream is = new ByteArrayInputStream(gif);
					ObjectMetadata metadata = new ObjectMetadata();
					metadata.setContentLength(gif.length);
					metadata.setContentType("image/gif");

					if (UrlValidator.getInstance().isValid(emojiName)) {
						emojiName = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9'))
								.retainFrom(StringUtils.substringAfterLast(emojiName, "/"));
					}

					String filenamePrefix = emojiName + "_approximated_";
					filenamePrefix = StringUtils
							.strip(filenamePrefix.replaceAll("ä", "a").replaceAll("ö", "o").replaceAll("å", "o"));
					if (!S3.fileExistsInBucket(filenamePrefix)) {
						S3.storeFileInBucket(filenamePrefix + System.currentTimeMillis() + ".gif", is, metadata);
					}

					LOG.info("Image stored in S3, publishing to topic s3-file-ready");
					SNS.publish("s3-file-ready", mapper.writeValueAsString(json)).get();
				} else {
					LOG.error("Gif generator returned an empty byte array, sending error response");
					SNS.publish("gif-generator-error", mapper.writeValueAsString(json)).get();
				}
			} catch (IOException | InterruptedException | ExecutionException e) {
				LOG.error("Exception occured when creating GIF.", e);
			}
		}

		Response responseBody = new Response("pprxmtr-gif-generator called.", new HashMap<>());
		return ApiGatewayResponse.builder().setStatusCode(200).setObjectBody(responseBody).build();
	}
}
