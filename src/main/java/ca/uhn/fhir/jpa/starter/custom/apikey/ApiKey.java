package ca.uhn.fhir.jpa.starter.custom.apikey;

import java.util.Arrays;

public class ApiKey {
	public enum Status {
		ACTIVE("active"),
		DISABLED("disabled");

		private final String status;

		Status(String status) {

			this.status = status;
		}

		public String getStatus() {
			return status;
		}

		public static Status get(String status) {
			return Arrays.stream(Status.values())
				.filter(s -> s.status.equals(status))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(String.format("Status %s is unknown", status)));
		}
	}

	private Long id;
	private String key;
	private String secret;
	private Status status;
	private String created;
	private String expires;
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

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getCreated() {
		return created;
	}

	public void setCreated(String created) {
		this.created = created;
	}

	public String getExpires() {
		return expires;
	}

	public void setExpires(String expires) {
		this.expires = expires;
	}

	public boolean isPermanent() {
		return permanent;
	}

	public void setPermanent(boolean permanent) {
		this.permanent = permanent;
	}
}
