package fi.pnsr.pprxmtr.sns;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ApiGatewayResponse {

	public static class Builder {

		private static final Logger LOG = LogManager.getLogger();

		private static final ObjectMapper objectMapper = new ObjectMapper();

		private boolean base64Encoded;
		private byte[] binaryBody;
		private Map<String, String> headers = Collections.emptyMap();
		private Object objectBody;
		private String rawBody;
		private int statusCode = 200;

		public ApiGatewayResponse build() {
			String body = null;
			if (rawBody != null) {
				body = rawBody;
			} else if (objectBody != null) {
				try {
					body = objectMapper.writeValueAsString(objectBody);
				} catch (JsonProcessingException e) {
					LOG.error("failed to serialize object", e);
					throw new RuntimeException(e);
				}
			} else if (binaryBody != null) {
				body = new String(Base64.getEncoder().encode(binaryBody), StandardCharsets.UTF_8);
			}
			return new ApiGatewayResponse(statusCode, body, headers, base64Encoded);
		}

		/**
		 * A binary or rather a base64encoded responses requires
		 * <ol>
		 * <li>"Binary Media Types" to be configured in API Gateway
		 * <li>a request with an "Accept" header set to one of the "Binary Media Types"
		 * </ol>
		 */
		public Builder setBase64Encoded(boolean base64Encoded) {
			this.base64Encoded = base64Encoded;
			return this;
		}

		/**
		 * Builds the {@link ApiGatewayResponse} using the passed binary body encoded as
		 * base64. {@link #setBase64Encoded(boolean) setBase64Encoded(true)} will be in
		 * invoked automatically.
		 */
		public Builder setBinaryBody(byte[] binaryBody) {
			this.binaryBody = binaryBody;
			setBase64Encoded(true);
			return this;
		}

		public Builder setHeaders(Map<String, String> headers) {
			this.headers = headers;
			return this;
		}

		/**
		 * Builds the {@link ApiGatewayResponse} using the passed object body converted
		 * to JSON.
		 */
		public Builder setObjectBody(Object objectBody) {
			this.objectBody = objectBody;
			return this;
		}

		/**
		 * Builds the {@link ApiGatewayResponse} using the passed raw body string.
		 */
		public Builder setRawBody(String rawBody) {
			this.rawBody = rawBody;
			return this;
		}

		public Builder setStatusCode(int statusCode) {
			this.statusCode = statusCode;
			return this;
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	private final String body;
	private final Map<String, String> headers;

	private final boolean isBase64Encoded;

	private final int statusCode;

	public ApiGatewayResponse(int statusCode, String body, Map<String, String> headers, boolean isBase64Encoded) {
		this.statusCode = statusCode;
		this.body = body;
		this.headers = headers;
		this.isBase64Encoded = isBase64Encoded;
	}

	public String getBody() {
		return body;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public int getStatusCode() {
		return statusCode;
	}

	// API Gateway expects the property to be called "isBase64Encoded" => isIs
	public boolean isIsBase64Encoded() {
		return isBase64Encoded;
	}
}
