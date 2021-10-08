package ca.uhn.fhir.jpa.starter.custom;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class InternalAuthenticationProvider implements AuthenticationProvider {
	private static final String INTERNAL_USER = "internal";
	private static final String INTERNAL_PASSWORD = "zEDf'S+^MC";


	@Override
	public Authentication authenticate(Authentication authentication)
		throws AuthenticationException {

		String name = authentication.getName();
		String password = authentication.getCredentials().toString();
		if (INTERNAL_USER.equals(name) && INTERNAL_PASSWORD.equals(password))
			return new UsernamePasswordAuthenticationToken(name, password, new ArrayList<>());
		throw new AuthenticationException("Access denied") {
		};
	}


	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}
}
