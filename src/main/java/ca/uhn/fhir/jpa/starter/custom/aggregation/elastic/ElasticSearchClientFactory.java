package ca.uhn.fhir.jpa.starter.custom.aggregation.elastic;

import ca.uhn.fhir.jpa.search.lastn.ElasticsearchRestClientFactory;
import ca.uhn.fhir.jpa.starter.EnvironmentHelper;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

@Component
public class ElasticSearchClientFactory {

	private final ConfigurableEnvironment env;

	public ElasticSearchClientFactory(ConfigurableEnvironment env) {
		this.env = env;
	}

	public RestHighLevelClient client() {
		if (!EnvironmentHelper.isElasticsearchEnabled(env)) {
			throw new IllegalStateException("Elasticsearch is switched off in config");
		}
		String url = EnvironmentHelper.getElasticsearchServerUrl(env);
		String host;
		if (url.startsWith("http")) {
			host = url.substring(url.indexOf("://") + 3, url.lastIndexOf(":"));
		} else {
			host = url.substring(0, url.indexOf(":"));
		}
		int elasticsearchPort = Integer.parseInt(url.substring(url.lastIndexOf(":") + 1));
		return ElasticsearchRestClientFactory.createElasticsearchHighLevelRestClient(host, elasticsearchPort,
			EnvironmentHelper.getElasticsearchServerUsername(env), EnvironmentHelper.getElasticsearchServerPassword(env));
	}
}
