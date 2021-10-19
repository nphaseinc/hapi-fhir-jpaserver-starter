package ca.uhn.fhir.jpa.starter.custom.aggregation.common;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.starter.custom.aggregation.rest.RestApiAggregator;
import ca.uhn.fhir.jpa.starter.custom.aggregation.elastic.ElasticSearchClientFactory;
import ca.uhn.fhir.jpa.starter.custom.aggregation.elastic.ElasticsearchAggregator;
import ca.uhn.fhir.to.TesterConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

@Component
public class DataAggregatorFactory {
	private static final String AGGREGATOR = "AGGREGATOR";
	private final StoppableDataAggregator aggregator;

	public DataAggregatorFactory(@Autowired ConfigurableEnvironment env, @Autowired TesterConfig myConfig) {
		this.aggregator = create(env, myConfig);
	}

	public StoppableDataAggregator getAggregator() {
		return aggregator;
	}

	private StoppableDataAggregator create(ConfigurableEnvironment env, TesterConfig myConfig) {
		String agg = env.getProperty(AGGREGATOR);
		if (ElasticsearchAggregator.class.getSimpleName().equals(agg)) {
			return new ElasticsearchAggregator(new ElasticSearchClientFactory(env));
		}

		FhirVersionEnum fhirVersion = myConfig.getIdToFhirVersion().entrySet().iterator().next().getValue();
		switch (fhirVersion) {
			case DSTU2:
				return new RestApiAggregator(FhirContext.forDstu2(),
					ca.uhn.fhir.model.dstu2.resource.Condition.class, ca.uhn.fhir.model.dstu2.resource.Bundle.class, myConfig);
			case DSTU3:
				return new RestApiAggregator(FhirContext.forDstu3(), org.hl7.fhir.dstu3.model.Condition.class,
					org.hl7.fhir.dstu3.model.Bundle.class, myConfig);
			default:
				throw new IllegalArgumentException(fhirVersion + " is not supported");
		}
	}
}
