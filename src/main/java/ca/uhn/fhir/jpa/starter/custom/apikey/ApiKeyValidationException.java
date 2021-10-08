package ca.uhn.fhir.jpa.starter.custom.apikey;

public class ApiKeyValidationException extends RuntimeException {
	public ApiKeyValidationException(String message) {
		super(message);
	}

	public ApiKeyValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
