package ca.uhn.fhir.jpa.starter.custom;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class UserController {

	@GetMapping("/user")
	public Principal user(Principal user) {
		return user;
	}

	@GetMapping(value = {"/auth-info"}, produces = MediaType.APPLICATION_JSON_VALUE)
	public Authentication getAuthentication() {
		return SecurityContextHolder.getContext().getAuthentication();
	}
}
