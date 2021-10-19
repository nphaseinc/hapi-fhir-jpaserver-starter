package ca.uhn.fhir.jpa.starter.custom.aggregation.common;


import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.Data;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.Disease;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface StoppableDataAggregator {

	Data aggregateDirect(int page, int size);

	Map<String, Disease> getCachedResult();

	void aggregate();

	void stop();

	boolean isDataUpdating();

	boolean aggregateDirectSupported();

	default Data aggregate(boolean cached) {
		if (cached) {
			return new Data(getCachedResult(), false);
		}
		if (!isDataUpdating()) {
			aggregate();//blocking
		} else {
			while (!isDataUpdating()) {
				Thread.onSpinWait();
			}
		}
		return new Data(getCachedResult(), false);
	}

	default Data aggregate(int page, int size, boolean cached) {
		int skip = page * size;
		int limit = skip + size;
		final Map<String, Disease> data = new ConcurrentHashMap<>();
		if (!cached)
			aggregate();
		getCachedResult().entrySet().stream().limit(limit).skip(skip).forEach(e -> data.put(e.getKey(), e.getValue()));
		boolean hasNext = getCachedResult().size() > limit;
		return new Data(data, hasNext);
	}
}
