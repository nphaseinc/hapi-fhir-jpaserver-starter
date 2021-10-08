package ca.uhn.fhir.jpa.starter.custom.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyDao extends JpaRepository<ApiKeyEntity, Long> {

	Optional<ApiKeyEntity> findByKey(String key);
}
