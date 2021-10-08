package ca.uhn.fhir.jpa.starter.custom.apikey;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ApiKeyServiceImpl implements ApiKeyService {
	@Autowired
	private ApiKeyDao apiKeyDao;

	public ApiKey generateNewKey(LocalDateTime dateTime) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ApiKeyEntity entity = new ApiKeyEntity();
		entity.setKey(RandomStringUtils.randomAlphanumeric(32));
		entity.setSecret(RandomStringUtils.randomAlphanumeric(32));
		entity.setCreated(LocalDateTime.now());
		entity.setStatus(ApiKey.Status.ACTIVE.getStatus());
		entity.setOwner((String) authentication.getPrincipal());
		if (dateTime == null)
			entity.setPermanent(true);
		else {
			entity.setPermanent(false);
			entity.setExpires(dateTime);
		}
		entity = apiKeyDao.save(entity);
		return map(entity);
	}

	@Override
	public void revokeKey(long id) {
		apiKeyDao.deleteById(id);
	}

	@Override
	@Transactional
	public ApiKey switchStatus(long id) {
		ApiKeyEntity byId = apiKeyDao.getById(id);
		String status = byId.getStatus();
		if(ApiKey.Status.ACTIVE.getStatus().equals(status))
			byId.setStatus(ApiKey.Status.DISABLED.getStatus());
		else
			byId.setStatus(ApiKey.Status.ACTIVE.getStatus());
		apiKeyDao.saveAndFlush(byId);
		return map(byId);
	}


	public List<ApiKey> getAllKeys(Principal principal) {
		return apiKeyDao.findAll()
			.stream()
			.filter(e -> e.getOwner().equals(principal.getName()))
			.map(this::map)
			.collect(Collectors.toList());
	}

	@Override
	public void validateKey(String key) {
		Optional<ApiKeyEntity> o = apiKeyDao.findByKey(key);
		ApiKeyEntity apiKey = o.orElseThrow(() -> new ApiKeyValidationException(String.format("Api key %s is not found", key)));
		if(ApiKey.Status.DISABLED.getStatus().equals(apiKey.getStatus())) {
			throw new ApiKeyValidationException(String.format("Api key %s is disabled", key));
		}
		if(!apiKey.isPermanent() && apiKey.getExpires().isBefore(LocalDateTime.now())) {
			throw new ApiKeyValidationException(String.format("Api key %s is expired", key));
		}
	}

	private ApiKey map(ApiKeyEntity entity) {
		ApiKey key = new ApiKey();
		key.setKey(entity.getKey());
		if(entity.isPermanent())
			key.setExpires("Never");
		else {
			String date = "";
			if(entity.getExpires() != null)
				date = entity.getExpires().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			key.setExpires(date);
		}
		String created = "";
		if(entity.getCreated() != null)
			created = entity.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		key.setCreated(created);
		key.setSecret(entity.getSecret());
		key.setStatus(ApiKey.Status.get(entity.getStatus()));
		key.setPermanent(entity.isPermanent());
		key.setId(entity.getId());
		return key;
	}
}
