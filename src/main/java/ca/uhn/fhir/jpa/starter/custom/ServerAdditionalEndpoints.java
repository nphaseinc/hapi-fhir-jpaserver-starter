package ca.uhn.fhir.jpa.starter.custom;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.to.TesterConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@RestController
public class ServerAdditionalEndpoints {

	private final TaskScheduler taskScheduler = new ConcurrentTaskScheduler();

	@Autowired
	ApiKeyService apiKeyService;
	@Autowired
	protected TesterConfig myConfig;

	private static LocalDateTime resultDate;
	private static ConditionSearch conditionSearch;

	private static final Map<String, Disease> DATA = new LinkedHashMap<>();

	@PostConstruct
	public void init() {
		FhirVersionEnum fhirVersion = myConfig.getIdToFhirVersion().entrySet().iterator().next().getValue();
		switch (fhirVersion) {
			case DSTU2:
				conditionSearch = new ConditionSearch(FhirContext.forDstu2(),
					ca.uhn.fhir.model.dstu2.resource.Condition.class, ca.uhn.fhir.model.dstu2.resource.Bundle.class);
				break;
			case DSTU3:
				conditionSearch = new ConditionSearch(FhirContext.forDstu3(), org.hl7.fhir.dstu3.model.Condition.class, org.hl7.fhir.dstu3.model.Bundle.class);
				break;
			default:
				throw new IllegalArgumentException(fhirVersion + " is not supported");
		}
	}

	private void waitPortAvailable() {
		boolean result = false;
		while (!result) {
			try {
				Socket s = new Socket("127.0.0.1", 8080);
				s.close();
				result = true;
			} catch (Exception ignore) {
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ignore) {
			}
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void doSearchAfterStartup() {
		new Thread(() -> {
			waitPortAvailable();
			taskScheduler.schedule(conditionSearch, new PeriodicTrigger(1, TimeUnit.DAYS));
		}).start();
	}

	@Operation(name = "$getSearchCondition", manualRequest = true, manualResponse = true)
	public void getSearchCondition(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String all = request.getParameter("all");
		final Map<String, Disease> ret;
		boolean hasNext = false;
		if (all == null || all.equals("false")) {
			final Map<String, Disease> data = new ConcurrentHashMap<>();
			String params = new String(IOUtils.toByteArray(request.getInputStream()));
			int page = getIntParam(params, "page", 0);
			int size = getIntParam(params, "size", 20);
			int skip = page * size;
			int limit = skip + size;
			DATA.entrySet().stream().limit(limit).skip(skip).forEach(e -> data.put(e.getKey(), e.getValue()));
			hasNext = DATA.size() > limit;
			ret = data;
		} else {
			ret = DATA;
		}
		Data data = new Data(ret, conditionSearch.dataUpdating, hasNext);
		response(response, () -> data);
	}

	private int getIntParam(String params, String name, int defaultVal) {
		try {
			Optional<Integer> o = Arrays.stream(params.split("&"))
				.map(s -> s.split("="))
				.filter(a -> a[0].equals(name))
				.findFirst()
				.map(a -> Integer.parseInt(a[1]));
			if (o.isPresent()) return o.get();
		} catch (Exception ignore) {
		}
		return defaultVal;
	}

	public void doSearchCondition() {
		new Thread(conditionSearch).start();
	}

	@Operation(name = "$forceRegenerate", manualRequest = true, manualResponse = true)
	public void forceRegenerate(HttpServletResponse response) throws IOException {
		if (!conditionSearch.dataUpdating) {
			doSearchCondition();
		}
		response.setContentType("application/json");
		String json = "{\"in-progress\" : \"" + conditionSearch.dataUpdating + "\"}";
		response.getWriter().write(json);
		response.getWriter().close();
	}

	@Operation(name = "$searchConditionDone", manualRequest = true, manualResponse = true)
	public void searchConditionDone(HttpServletResponse response) throws IOException {
		if (!conditionSearch.dataUpdating && resultDate == null) {
			doSearchCondition();
		}
		response.setContentType("application/json");
		String json = "{\"in-progress\" : \"" + conditionSearch.dataUpdating + "\"" +
			(resultDate != null ? ", \"result-date\" : \"" + resultDate + "\"" : "") +
			"}";
		response.getWriter().write(json);
		response.getWriter().close();
	}

	@Operation(name = "$generateApiKey", manualRequest = true, manualResponse = true)
	public void generateApiKey(HttpServletResponse response) throws IOException {
		response(response, () ->apiKeyService.generateNewKey());
	}

	@Operation(name = "$getApiKeys", manualRequest = true, manualResponse = true)
	public void getApiKeys(HttpServletResponse response) throws IOException {
		response(response, () ->apiKeyService.getAllKeys());
	}

	private void response(HttpServletResponse response, Supplier<?> dataSupplier) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		response.setContentType("application/json");
		objectMapper.writeValue(response.getWriter(), dataSupplier.get());
		response.getWriter().close();
	}

	private final class ConditionSearch implements Runnable {
		private final FhirContext ctx;
		private final Class<? extends IBaseResource> aClass;
		private final Class<? extends IBaseBundle> bundleClass;
		private volatile boolean dataUpdating = false;
		private String nextURL;

		public ConditionSearch(FhirContext ctx, Class<? extends IBaseResource> aClass, Class<? extends IBaseBundle> bundleClass) {
			this.ctx = ctx;
			this.aClass = aClass;
			this.bundleClass = bundleClass;
		}

		@Override
		public synchronized void run() {
			try {
				dataUpdating = true;
				Map<String, Disease> d = new LinkedHashMap<>();
				String serverBase = myConfig.getIdToServerBase().entrySet().iterator().next().getValue();
				ctx.getRestfulClientFactory().setSocketTimeout(15 * 60 * 1000);
				ctx.getRestfulClientFactory().setConnectTimeout(15 * 60 * 1000);
				ctx.getRestfulClientFactory().setConnectionRequestTimeout(15 * 60 * 1000);
				IGenericClient client = ctx.newRestfulGenericClient(serverBase);
				IBaseBundle result = repeat(3, () ->
					client
						.search()
						.forResource(aClass)
						.count(1000)
						.returnBundle(bundleClass)
						.execute()
				);
				while (hasNext(result, d)) {
					result = next();
					nextURL = null;
				}
				resultDate = LocalDateTime.now();
				DATA.clear();
				DATA.putAll(d);
				d.clear();
			} finally {
				dataUpdating = false;
			}
		}

		private <R> R repeat(int times, Supplier<R> function) {
			try {
				return function.get();
			} catch (Exception e) {
				if (times - 1 > 0)
					return repeat(times - 1, function);
				else
					throw e;
			}
		}

		private boolean hasNext(IBaseBundle bundle, Map<String, Disease> dataMap) {
			if (bundle instanceof ca.uhn.fhir.model.dstu2.resource.Bundle) {
				ca.uhn.fhir.model.dstu2.resource.Bundle b = (ca.uhn.fhir.model.dstu2.resource.Bundle) bundle;
				var link = b.getLink();
				if (link.size() > 1) {
					String relation = link.get(1).getRelation();
					if ("next".equals(relation))
						nextURL = link.get(1).getUrl();
					else
						nextURL = null;
				} else
					nextURL = null;
				b.getEntry().forEach(e -> {
					ca.uhn.fhir.model.dstu2.resource.Condition c = (ca.uhn.fhir.model.dstu2.resource.Condition) e.getResource();
					var codingDt = c.getCode().getCoding().get(0);
					String code = codingDt.getCode();
					String system = codingDt.getSystem();
					String display = codingDt.getDisplay();
					Disease disease = new Disease(system, code, display);
					add(disease, dataMap);
				});
			} else if (bundle instanceof org.hl7.fhir.dstu3.model.Bundle) {
				org.hl7.fhir.dstu3.model.Bundle b = (org.hl7.fhir.dstu3.model.Bundle) bundle;
				var link = b.getLink();
				if (link.size() > 1) {
					String relation = link.get(1).getRelation();
					if ("next".equals(relation))
						nextURL = link.get(1).getUrl();
					else
						nextURL = null;
				} else
					nextURL = null;
				b.getEntry().forEach(e -> {
					org.hl7.fhir.dstu3.model.Condition c = (org.hl7.fhir.dstu3.model.Condition) e.getResource();
					var codingDt = c.getCode().getCoding().get(0);
					String code = codingDt.getCode();
					String system = codingDt.getSystem();
					String display = codingDt.getDisplay();
					Disease disease = new Disease(system, code, display);
					//String patient = c.getSubject().getReference();
					add(disease, dataMap);
				});
			}

			return nextURL != null;
		}

		private void add(Disease disease, Map<String, Disease> dataMap) {
			dataMap.compute(disease.code, (k, d) -> {
				if (d == null)
					d = disease;
				//d.persons.add(patient);
				d.personCount++;
				return d;
			});
		}

		private IBaseBundle next() {
			String serverBase = myConfig.getIdToServerBase().entrySet().iterator().next().getValue();
			IGenericClient client = ctx.newRestfulGenericClient(serverBase);
			return repeat(3, () -> client
				.search()
				.byUrl(nextURL)
				.returnBundle(bundleClass)
				.execute());
		}
	}
	@SuppressWarnings("unused")
	private static class Data {
		private final Map<String, Disease> data;
		private final boolean dataUpdating;
		private final boolean hasNext;

		public Data(Map<String, Disease> data, boolean dataUpdating, boolean hasNext) {
			this.data = data;
			this.dataUpdating = dataUpdating;
			this.hasNext = hasNext;
		}

		public Map<String, Disease> getData() {
			return data;
		}

		public boolean isDataUpdating() {
			return dataUpdating;
		}

		public boolean isHasNext() {
			return hasNext;
		}
	}

	private static class Disease {
		private final String system;
		private final String code;
		private final String display;
		private int personCount = 0;

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

		public int getPersonCount() {
			return personCount;
		}
	}
}
