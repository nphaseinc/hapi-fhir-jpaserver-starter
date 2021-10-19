package ca.uhn.fhir.jpa.starter.custom.aggregation.dto;

import java.util.Map;

public class Data {
	private final Map<String, Disease> data;
	private final boolean hasNext;

	public Data(Map<String, Disease> data, boolean hasNext) {
		this.data = data;
		this.hasNext = hasNext;
	}

	public Map<String, Disease> getData() {
		return data;
	}

	public boolean isHasNext() {
		return hasNext;
	}
}
