package ca.uhn.fhir.jpa.starter.custom.aggregation.dto;

import java.util.Objects;

public class Disease {
	private final String system;
	private final String code;
	private final String display;
	private long personCount = 0;

	public Disease(String system, String code, String display) {
		this.system = system;
		this.code = code;
		this.display = display;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Disease disease = (Disease) o;
		return Objects.equals(system, disease.system) && Objects.equals(code, disease.code);
	}

	@Override
	public int hashCode() {
		return Objects.hash(system, code);
	}

	public String getSystem() {
		return system;
	}

	public String getCode() {
		return code;
	}

	public String getDisplay() {
		return display;
	}

	public long getPersonCount() {
		return personCount;
	}

	public void increment() {
		personCount++;
	}

	public void setPersonCount(long personCount) {
		this.personCount = personCount;
	}
}
