package ca.uhn.fhir.jpa.starter.custom.apikey;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Table(name = "CUSTOM_API_KEY")
@Entity
public class ApiKeyEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "ID", nullable = false)
	private Long id;

	@Column(name = "KEY", length = 256, nullable = false, unique = true)
	private String key;
	@Column(name = "SECRET", length = 256, nullable = false)
	private String secret;
	@Column(name = "OWNER", length = 256, nullable = false)
	private String owner;
	@Column(name = "STATUS", nullable = false)
	@Fetch(value = FetchMode.SELECT)
	private String status;
	@Column(name = "CREATED", nullable = false, updatable = false)
	private LocalDateTime created;
	@Column(name = "EXPIRED")
	private LocalDateTime expires;
	@Column(name = "PERMANENT", nullable = false)
	private boolean permanent;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getCreated() {
		return created;
	}

	public void setCreated(LocalDateTime created) {
		this.created = created;
	}

	public LocalDateTime getExpires() {
		return expires;
	}

	public void setExpires(LocalDateTime expires) {
		this.expires = expires;
	}

	public boolean isPermanent() {
		return permanent;
	}

	public void setPermanent(boolean neverExpires) {
		this.permanent = neverExpires;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}
}