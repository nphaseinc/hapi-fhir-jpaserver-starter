package ca.uhn.fhir.jpa.starter.custom.aggregation.elastic;

import ca.uhn.fhir.jpa.entity.ResourceSearchView;
import ca.uhn.fhir.jpa.starter.custom.aggregation.util.GZIPCompression;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dao.IResourceSearchViewExtDao;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.ElasticCondition;
import ca.uhn.fhir.jpa.starter.custom.aggregation.exceptions.AggregationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
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
	private final IResourceSearchViewExtDao extDao;
	private final RestHighLevelClient client;

	public Indexer(IResourceSearchViewExtDao extDao, ElasticSearchClientFactory factory) {
		this.extDao = extDao;
		client = factory.client();
	}

	@Transactional
	public void doIndex() {
		if (!createIndex(CONDITION_INDEX, null)) throw new AggregationException("Unable to create index in Elasticsearch");
		Long latestPid = getLatest();
		Pageable pageable = Pageable.ofSize(100);
		Collection<ResourceSearchView> views;
		System.err.println("STARTED::doIndex");
		do {
			views = extDao.findByResourceId(latestPid, "Condition", pageable);
			views.forEach(this::indexInElastic);
			pageable = pageable.next();
			System.err.println("PROCESSED " + views.size());
		} while (!views.isEmpty());
		System.err.println("FINISH: doIndex");
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
			System.err.println(e.getMessage());
		}
		return 0L;
	}

	private boolean indexInElastic(ResourceSearchView view) {
		IndexResponse indexResponse;
		try {
			IndexRequest request = new IndexRequest(CONDITION_INDEX);
			request.id(String.valueOf(view.getId()));
			request.source(map(view), XContentType.JSON);
			indexResponse = client.index(request, RequestOptions.DEFAULT);
		} catch (Exception e) {
			return false;
		}
		return (indexResponse.getResult() == DocWriteResponse.Result.CREATED) || (indexResponse.getResult() == DocWriteResponse.Result.UPDATED);
	}

	private byte[] map(ResourceSearchView view) {
		try {
			String decompress = GZIPCompression.decompress(view.getResource());
			ObjectMapper mapper = new ObjectMapper();
			TypeReference<HashMap<String,Object>> typeRef = new TypeReference<>() {};
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
