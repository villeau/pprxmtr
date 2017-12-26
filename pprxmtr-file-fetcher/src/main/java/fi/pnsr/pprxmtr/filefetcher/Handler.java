package fi.pnsr.pprxmtr.filefetcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.CharMatcher;

import fi.pnsr.pprxmtr.s3.S3;
import fi.pnsr.pprxmtr.sns.ApiGatewayResponse;
import fi.pnsr.pprxmtr.sns.Response;
import fi.pnsr.pprxmtr.sns.SNS;

public class Handler implements RequestHandler<SNSEvent, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger();

	private ResourceBundle resourceBundle = null;

	@Override
	public ApiGatewayResponse handleRequest(SNSEvent input, Context context) {

		if (CollectionUtils.isNotEmpty(input.getRecords())) {
			SNSRecord record = input.getRecords().get(0);
			if (StringUtils.containsIgnoreCase(record.getSNS().getTopicArn(), "handle-emoji")) {
				LOG.info("Got message to handle-emoji topic.");
				handleSlackEvent(record);
			} else if (StringUtils.containsIgnoreCase(record.getSNS().getTopicArn(), "s3-file-ready")) {
				LOG.info("Got message to s3-file-ready topic.");
				postImageToSlack(record);
			} else if (StringUtils.containsIgnoreCase(record.getSNS().getTopicArn(), "gif-generator-error")) {
				LOG.info("Got message to gif-generator-error topic.");
				postErrorMessageToSlack(record);
			}
		}

		Response responseBody = new Response("pprxmtr-file-fetcher called succesfully.", new HashMap<>());
		return ApiGatewayResponse.builder().setStatusCode(200).setObjectBody(responseBody).build();
	}

	private void handleSlackEvent(SNSRecord record) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json = null;
		try {
			json = (ObjectNode) mapper.readTree(record.getSNS().getMessage());
			LOG.trace(mapper.writeValueAsString(json));

			if (json.has("oauth")) {

				// 1. Fetch team custom emojis from Slack Web Api.
				HttpClient client = HttpClientBuilder.create().build();
				HttpPost post = new HttpPost("https://slack.com/api/emoji.list");
				NameValuePair nvp = new BasicNameValuePair("token", json.get("oauth").asText());
				post.setEntity(new UrlEncodedFormEntity(Arrays.asList(nvp)));
				HttpResponse response = client.execute(post);
				int status = response.getStatusLine().getStatusCode();
				String slackApiResponseContent = IOUtils.toString(response.getEntity().getContent(),
						StandardCharsets.UTF_8);

				LOG.info("Got {} status from Slack API, message: {}", status, slackApiResponseContent);

				resourceBundle = ResourceBundle.getBundle("Messages", new Locale(json.get("locale").asText()));
				JsonNode emojiJson = mapper.readTree(slackApiResponseContent).get("emoji");
				String emojiNameUnfiltered = StringUtils.strip(json.get("text").asText());
				String emojiName = StringUtils.removeAll(emojiNameUnfiltered, ":");

				// 2. Check if the emoji given is an existing custom emoji or an alias to one.
				if (emojiJson.has(emojiName)) {
					String emojiUrl = emojiJson.get(emojiName).asText();

					if (StringUtils.startsWithIgnoreCase(emojiUrl, "alias")) {
						String resolvedAlias = StringUtils.substringAfter(emojiUrl, ":");
						if (emojiJson.has(resolvedAlias)) {
							emojiUrl = emojiJson.get(resolvedAlias).asText();
						} else {
							LOG.info("Alias pointed to standard emoji, cannot approximate.");
							sendSlackTextResponse(json, resolveMessage("standardEmojiResponse", resolvedAlias));
						}
					}

					String filenamePrefix = emojiName + "_approximated_";
					String s3Key = S3.getS3KeyFromBucket(filenamePrefix);

					if (StringUtils.isNotEmpty(s3Key)) {
						LOG.info("Emoji already approximated, just return URL!");
						sendSlackImageResponse(json, s3Key);
					} else if (StringUtils.isNotBlank(emojiUrl)) {
						retrieveImageAndSendToGifGenerator(mapper, json, client, emojiUrl);
					}

					// 3. If the emoji wasn't a custom emoji or an alias to one, check if it's a
					// valid link to an arbitrary image.
				} else if (UrlValidator.getInstance().isValid(emojiNameUnfiltered)) {

					String filenamePrefix = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9'))
							.retainFrom(StringUtils.substringAfterLast(emojiNameUnfiltered, "/")) + "_approximated_";
					String s3Key = S3.getS3KeyFromBucket(filenamePrefix);

					if (StringUtils.isNotEmpty(s3Key)) {
						LOG.info("Emoji already approximated, just return URL!");
						sendSlackImageResponse(json, s3Key);
					} else {
						retrieveImageAndSendToGifGenerator(mapper, json, client, emojiNameUnfiltered);
					}

					// 4. Finally check if the emoji given is a standard emoji that cannot currently
					// be approximated.
				} else {
					LOG.error("No emoji or alias found with name given or was standard emoji!");
					sendSlackTextResponse(json, resolveMessage("standardEmojiResponse", emojiName));
				}
			}
		} catch (IOException | InterruptedException | ExecutionException e) {
			LOG.error("Uh oh!", e);
			sendSlackTextResponse(json, resolveMessage("errorResponse"));
		}
	}

	private void postErrorMessageToSlack(SNSRecord record) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json = null;
		try {
			json = (ObjectNode) mapper.readTree(record.getSNS().getMessage());
			sendSlackTextResponse(json, resolveMessage("errorResponse"));
			LOG.info("Error message posted to Slack.");
		} catch (IOException e) {
			LOG.error("Uh oh!", e);
		}
	}

	private void postImageToSlack(SNSRecord record) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json = null;
		try {
			json = (ObjectNode) mapper.readTree(record.getSNS().getMessage());
			String emojiName = json.get("text").asText();

			if (UrlValidator.getInstance().isValid(emojiName)) {
				emojiName = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9'))
						.retainFrom(StringUtils.substringAfterLast(emojiName, "/"));
			}

			String filenamePrefix = emojiName + "_approximated_";
			String s3Key = S3.getS3KeyFromBucket(filenamePrefix);
			sendSlackImageResponse(json, s3Key);
			LOG.info("Image {} posted to Slack.", s3Key);
		} catch (IOException e) {
			LOG.error("Uh oh!", e);
		}
	}

	private String resolveMessage(String key, Object... args) {
		String pattern = resourceBundle.getString(key);
		return MessageFormat.format(pattern, args);
	}

	private void retrieveImageAndSendToGifGenerator(ObjectMapper mapper, ObjectNode json, HttpClient client,
			String emojiUrl) throws IOException, ClientProtocolException, InterruptedException, ExecutionException,
			JsonProcessingException {

		HttpGet getImageRequest = new HttpGet(emojiUrl);
		HttpResponse getImageResponse = client.execute(getImageRequest);
		int getImageStatus = getImageResponse.getStatusLine().getStatusCode();
		LOG.info("Got {} status from Slack API after fetching emoji file.", getImageStatus);

		if (StringUtils.contains(getImageResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue(), "image")) {

			byte[] imageFile = IOUtils.toByteArray(getImageResponse.getEntity().getContent());
			String base64 = Base64.encodeBase64String(imageFile);
			json.put("image", base64);

			LOG.info("Sending image to Gif Generator");
			SNS.publish("generate-gif", mapper.writeValueAsString(json)).get();

		} else {

			LOG.info("Tried to fetch image from from {} but returned content was not an image according to headers!",
					emojiUrl);
			sendSlackTextResponse(json, resolveMessage("errorResponse"));
		}
	}

	private void sendSlackImageResponse(ObjectNode json, String s3Key) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode message = mapper.createObjectNode();
			ArrayNode attachments = mapper.createArrayNode();
			ObjectNode attachment = mapper.createObjectNode();

			String emoji = json.get("text").asText();

			if (UrlValidator.getInstance().isValid(emoji)) {
				attachment.put("title_link", emoji);
				emoji = StringUtils.substringAfterLast(emoji, "/");
			}

			String username = json.get("user_name").asText();
			String responseUrl = json.get("response_url").asText();
			String slackChannelId = json.get("channel_id").asText();

			message.put("response_type", "in_channel");
			message.put("channel_id", slackChannelId);
			attachment.put("title", resolveMessage("slackImageResponse", emoji, username));
			attachment.put("fallback", "Approksimoitu " + emoji);
			attachment.put("image_url", "https://s3.amazonaws.com/approximated-gifs/" + s3Key);
			attachments.add(attachment);
			message.set("attachments", attachments);

			HttpClient client = HttpClientBuilder.create().build();
			HttpPost slackResponseReq = new HttpPost(responseUrl);
			slackResponseReq
					.setEntity(new StringEntity(mapper.writeValueAsString(message), ContentType.APPLICATION_JSON));
			HttpResponse slackResponse = client.execute(slackResponseReq);
			int status = slackResponse.getStatusLine().getStatusCode();
			LOG.info("Got {} status from Slack API after sending approximation to response url.", status);
		} catch (UnsupportedOperationException | IOException e) {
			LOG.error("Exception occured when sending Slack response", e);
		}
	}

	private void sendSlackTextResponse(ObjectNode json, String message) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode messageNode = mapper.createObjectNode();

			String responseUrl = json.get("response_url").asText();
			String slackChannelId = json.get("channel_id").asText();

			messageNode.put("text", message);
			messageNode.put("channel_id", slackChannelId);

			HttpClient client = HttpClientBuilder.create().build();
			HttpPost slackResponseReq = new HttpPost(responseUrl);
			slackResponseReq
					.setEntity(new StringEntity(mapper.writeValueAsString(messageNode), ContentType.APPLICATION_JSON));
			HttpResponse slackResponse = client.execute(slackResponseReq);
			int status = slackResponse.getStatusLine().getStatusCode();
			LOG.info("Got {} status from Slack API after sending request to response url.", status);
		} catch (UnsupportedOperationException | IOException e) {
			LOG.error("Exception occured when sending Slack response", e);
		}
	}

}
