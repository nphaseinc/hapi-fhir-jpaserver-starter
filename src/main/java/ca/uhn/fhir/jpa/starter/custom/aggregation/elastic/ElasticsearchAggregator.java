package ca.uhn.fhir.jpa.starter.custom.aggregation.elastic;

import ca.uhn.fhir.jpa.starter.custom.aggregation.common.StoppableDataAggregator;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.Data;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.Disease;
import ca.uhn.fhir.jpa.starter.custom.aggregation.exceptions.AggregationException;
import ca.uhn.fhir.jpa.starter.custom.aggregation.exceptions.StopException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static ca.uhn.fhir.jpa.starter.custom.aggregation.elastic.Indexer.CONDITION_INDEX;

public class ElasticsearchAggregator implements StoppableDataAggregator {
	private static final Logger LOG =  LoggerFactory.getLogger(ElasticsearchAggregator.class);

	private final RestHighLevelClient client;
	private Map<String, Disease> LATEST = new ConcurrentHashMap<>();
	private volatile boolean dataUpdating = false;
	private volatile boolean stop = false;

	public ElasticsearchAggregator(ElasticSearchClientFactory factory) {
		client = factory.client();
	}

	public synchronized void aggregate() {
		LATEST = aggregateInternal(0, 0).getData();
	}

	private synchronized Data aggregateInternal(int page, int size) {
		try {
			dataUpdating = true;
			stop = false;
			int newSize = (page + 1) * size;
			if (newSize == 0) newSize = 100000000;
			SearchRequest searchRequest = new SearchRequest(CONDITION_INDEX)
				.source(new SearchSourceBuilder()
						.size(0)
						.aggregation(AggregationBuilders.terms("by_code").field("code.keyword").size(newSize))
				);
			SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
			List<? extends Terms.Bucket> buckets = ((ParsedStringTerms) search.getAggregations().get("by_code")).getBuckets();
			long sumOfOtherDocCounts = ((ParsedStringTerms) search.getAggregations().get("by_code")).getSumOfOtherDocCounts();

			return new Data(buckets.stream()
				.skip((long) page * size)
				.map(this::toDisease)
				.collect(Collectors.toMap(Disease::getCode, d -> d)), sumOfOtherDocCounts > 0);
		} catch (Exception e) {
			String message = "Unable to aggregate data in Elasticsearch";
			LOG.error(message, e);
			throw new AggregationException(message, e);
		} finally {
			dataUpdating = false;
			stop = false;
		}
	}


	@Override
	public Data aggregateDirect(int page, int size) {
		return aggregateInternal(page, size);
	}

	@Override
	public Map<String, Disease> getCachedResult() {
		return LATEST;
	}

	@Override
	public void stop() {
		stop = true;
	}

	@Override
	public boolean isDataUpdating() {
		return dataUpdating;
	}

	@Override
	public boolean aggregateDirectSupported() {
		return true;
	}

	private Disease toDisease(Terms.Bucket bucket) {
		if (stop) throw new StopException();
		String code = bucket.getKeyAsString();
		String display = "";
		String system = "";
		try {
			SearchRequest searchRequest = new SearchRequest(CONDITION_INDEX).source(
				new SearchSourceBuilder()
					.size(1)
					.query(QueryBuilders.termQuery("code.keyword", code))
			);
			SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
			SearchHits hits = search.getHits();
			SearchHit at = hits.getAt(0);
			Map<String, Object> map = at.getSourceAsMap();
			system = (String) map.get("system");
			display = (String) map.get("display");
		} catch (Exception e) {
			LOG.warn("Unable to retrieve Disease data from CONDITION_INDEX", e);
		}
		Disease disease = new Disease(system, code, display);
		disease.setPersonCount(bucket.getDocCount());
		return disease;
	}
}
