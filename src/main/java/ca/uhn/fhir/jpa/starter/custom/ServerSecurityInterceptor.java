package ca.uhn.fhir.jpa.starter.custom;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import org.apache.commons.codec.binary.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Interceptor
public class ServerSecurityInterceptor {
	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean incomingRequestPostProcessed(RequestDetails theRequestDetails, HttpServletRequest theRequest, HttpServletResponse theResponse) throws AuthenticationException {
		String authHeader = theRequest.getHeader("Authorization");

		// The format of the header must be:
		// Authorization: Basic [base64 of username:password]
		if(true) return true;
		String url = theRequestDetails.getCompleteUrl();
		if(url.endsWith("/fhir/$login") || url.endsWith("/metadata"))
			return true;

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			System.err.println(theRequest.getSession());
			System.err.println(theRequestDetails);
			//if(theRequest.getSession().getAttribute("auth") == null)
				//throw new AuthenticationException("Missing or invalid Authorization header");
		}

		String base64 = authHeader.substring("Basic ".length());
		String base64decoded = new String(Base64.decodeBase64(base64));
		String[] parts = base64decoded.split(":");

		String username = parts[0];
		String password = parts[1];

		/*
		 * Here we test for a hardcoded username & password. This is
		 * not typically how you would implement this in a production
		 * system of course..
		 */
		if (!username.equals("someuser") || !password.equals("thepassword")) {
			throw new AuthenticationException("Invalid username or password");
		}

		// Return true to allow the request to proceed
		return true;
	}
}
