package ca.uhn.fhir.jpa.starter.custom;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;

import static org.apache.http.HttpHeaders.AUTHORIZATION;

@Component
public class RCCAuthenticationProvider implements AuthenticationProvider {
	private static final String url = "https://rcdev.redcapcloud.com/rest/v2/ipass/auth";

	@Override
	public Authentication authenticate(Authentication authentication)
		throws AuthenticationException {

		String name = authentication.getName();
		String password = authentication.getCredentials().toString();

		HttpResponse<String> response;
		try {
			response = authenticateAgainstThirdPartySystem(name, password, url);
		} catch (Exception e) {
			throw new AuthenticationException(e.getMessage()) {};
		}
		if (response.statusCode() >= 200 && response.statusCode() < 210) {
			JsonObject object = new Gson().fromJson(response.body(), JsonObject.class);
			// use the credentials
			// and authenticate against the third-party system
			if (object.has("sub") || object.has("name"))
				return new UsernamePasswordAuthenticationToken(name, password, new ArrayList<>());
			else {
				throw new AuthenticationException(getError(response)) {
				};
			}
		} else {
			throw new AuthenticationException(getError(response)) {
			};
		}
	}

	private String getError(HttpResponse<String> response) {
		try {
			JsonObject object = new Gson().fromJson(response.body(), JsonObject.class);
			if(object.has("error")) return object.get("error").getAsString();
		} catch (JsonSyntaxException ignore) {
		}
		return response.body();
	}

	private HttpResponse<String> authenticateAgainstThirdPartySystem(String login, String password, String url) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.build();
		byte[] auth = (login + ":" + password).getBytes(StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofMinutes(1))
			.header(AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(auth))
			.GET()
			.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}
}
