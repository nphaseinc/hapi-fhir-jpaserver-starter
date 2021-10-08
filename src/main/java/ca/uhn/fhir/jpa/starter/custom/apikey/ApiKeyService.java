package ca.uhn.fhir.jpa.starter.custom.apikey;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

public interface ApiKeyService {
	List<ApiKey> getAllKeys(Principal principal);

	ApiKey generateNewKey(LocalDateTime dateTime);

	void revokeKey(long id);

	ApiKey switchStatus(long id);

	void validateKey(String key);
}
