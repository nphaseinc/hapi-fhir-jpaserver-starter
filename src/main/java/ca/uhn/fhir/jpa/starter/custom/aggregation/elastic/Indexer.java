package ca.uhn.fhir.jpa.starter.custom.aggregation.elastic;

import ca.uhn.fhir.jpa.entity.ResourceSearchView;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dao.IResourceSearchViewExtDao;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.ElasticCondition;
import ca.uhn.fhir.jpa.starter.custom.aggregation.exceptions.AggregationException;
import ca.uhn.fhir.jpa.starter.custom.aggregation.util.GZIPCompression;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Indexer {

	public static final String CONDITION_INDEX = "condition_index";
	private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);
	private final IResourceSearchViewExtDao extDao;
	private final RestHighLevelClient client;

	public Indexer(IResourceSearchViewExtDao extDao, ElasticSearchClientFactory factory) {
		this.extDao = extDao;
		client = factory.client();
	}

	@Transactional
	public void doIndex() {
		if (!createIndex(CONDITION_INDEX, null))
			throw new AggregationException("Unable to create index in Elasticsearch");
		Long latestPid = getLatest();
		Pageable pageable = Pageable.ofSize(1000);
		Collection<ResourceSearchView> views;
		LOG.info("STARTED::doIndex");
		long processed = 0;
		do {
			views = extDao.findByResourceId(latestPid, "Condition", pageable);
			if(!views.isEmpty()) {
				BulkRequest bulkRequest = new BulkRequest();
				views.forEach(v -> addToRequest(v, bulkRequest));
				if (!indexInElastic(bulkRequest))
					throw new AggregationException("Unable to add data to Elastic");
				processed += views.size();
				pageable = pageable.next();
				LOG.info("PROCESSED " + processed);
			}
		} while (!views.isEmpty());
		LOG.info("FINISH: doIndex");
	}

	private Long getLatest() {
		SearchRequest searchRequest = new SearchRequest(CONDITION_INDEX)
			.source(new SearchSourceBuilder()
				.size(1)
				.query(QueryBuilders.matchAllQuery())
				.sort("_id", SortOrder.DESC)
				.trackTotalHits(true)
			);
		try {
			SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
			String id = search.getHits().getAt(0).getId();
			return Long.getLong(id);
		} catch (Exception e) {
			LOG.warn("Unable to get get latest row ID for CONDITION_INDEX", e);
		}
		return 0L;
	}

	private void addToRequest(ResourceSearchView view, BulkRequest bulkRequest) {
		IndexRequest request = new IndexRequest(CONDITION_INDEX);
		request.id(String.valueOf(view.getId()));
		request.source(map(view), XContentType.JSON);
		bulkRequest.add(request);
	}

	private boolean indexInElastic(BulkRequest bulkRequest) {
		BulkResponse response;
		try {
			response = client.bulk(bulkRequest, RequestOptions.DEFAULT);
		} catch (Exception e) {
			return false;
		}
		return response != null && !response.hasFailures();
	}

	private byte[] map(ResourceSearchView view) {
		try {
			String decompress = GZIPCompression.decompress(view.getResource());
			ObjectMapper mapper = new ObjectMapper();
			TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {
			};
			HashMap<String, Object> value = mapper.readValue(decompress, typeRef);
			ElasticCondition condition = createCondition(value);
			return mapper.writeValueAsBytes(condition);
		} catch (Exception e) {
			throw new AggregationException("Unable to create ElasticCondition object for index");
		}
	}

	@SuppressWarnings("unchecked")
	private ElasticCondition createCondition(HashMap<String, Object> value) {
		ElasticCondition condition = new ElasticCondition();
		Map<?, String> subject = (Map<?, String>) value.get("subject");
		String patient = subject.getOrDefault("reference", "");
		condition.setPatient(patient);
		Map<String, Object> code = (Map<String, Object>) value.get("code");
		List<Object> coding = (List<Object>) code.get("coding");
		Map<String, Object> o = (Map<String, Object>) coding.get(0);
		String system = (String) o.getOrDefault("system", "");
		condition.setSystem(system);
		String c = (String) o.getOrDefault("code", "");
		condition.setCode(c);
		String display = (String) o.getOrDefault("display", "");
		condition.setDisplay(display);
		return condition;
	}

	private boolean createIndex(String theIndexName, String theMapping) {
		try {
			if (!indexExists(CONDITION_INDEX)) {
				CreateIndexRequest request = new CreateIndexRequest(theIndexName);
				if (theMapping != null)
					request.source(theMapping, XContentType.JSON);
				CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
				return createIndexResponse.isAcknowledged();
			}
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	private boolean indexExists(String theIndexName) {
		GetIndexRequest request = new GetIndexRequest(theIndexName);
		try {
			return client.indices().exists(request, RequestOptions.DEFAULT);
		} catch (IOException e) {
			return false;
		}
	}
}
