package ca.uhn.fhir.jpa.starter.custom.aggregation.exceptions;

public class AggregationException extends RuntimeException {
	public AggregationException(String message, Throwable throwable) {
		super(message, throwable);
	}

	public AggregationException(String message) {
		super(message);
	}
}
