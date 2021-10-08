package ca.uhn.fhir.jpa.starter.custom;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.starter.custom.apikey.ApiKeyService;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Interceptor
public class ServerSecurityInterceptor {

	private ApiKeyService apiKeyService;

	public ServerSecurityInterceptor(ApiKeyService apiKeyService) {
		this.apiKeyService = apiKeyService;
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean incomingRequestPostProcessed(RequestDetails reqDetails, HttpServletRequest req, HttpServletResponse resp) throws AuthenticationException {
		String url = reqDetails.getCompleteUrl();
		if (url.endsWith("/fhir/$login") || url.endsWith("/metadata"))
			return true;

		if (urlContainsValidApikey(url)) {
			return true;
		}

		if (checkBearerToken(reqDetails, req)) {
			return true;
		}

		// Return true to allow the request to proceed
		return true;
	}

	private boolean checkBearerToken(RequestDetails theRequestDetails, HttpServletRequest theRequest) {
		String authHeader = theRequest.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			//throw new AuthenticationException("Missing or invalid Authorization header");
			return false;
		}
		String token = authHeader.substring("Bearer ".length());
		if (ServerAdditionalEndpoints.INTERNAL_TOKEN.equals(token)) return true;
		//todo validate oauth
		return true;
	}

	private boolean urlContainsValidApikey(String completeUrl) {
		if (completeUrl.indexOf("apikey") > 0) {
			String apikey = completeUrl.substring(completeUrl.indexOf("apikey"));
			if (apikey.indexOf("&") > 0) apikey = apikey.substring(0, apikey.indexOf("&"));
			String[] split = apikey.split("=");
			if (split.length == 2) {
				String key = split[1];
				try {
					apiKeyService.validateKey(key);
					return true;
				} catch (Exception e) {
					throw new AuthenticationException(String.format("API key %s is not valid", key), e);
				}
			}
		}
		return false;
	}
}
