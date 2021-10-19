package ca.uhn.fhir.jpa.starter.custom.aggregation.rest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.custom.aggregation.common.StoppableDataAggregator;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.Data;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.Disease;
import ca.uhn.fhir.jpa.starter.custom.aggregation.exceptions.StopException;
import ca.uhn.fhir.jpa.starter.custom.auth.ServerSecurityInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.to.TesterConfig;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


public class RestApiAggregator implements StoppableDataAggregator {
	private final FhirContext ctx;
	private final Class<? extends IBaseResource> aClass;
	private final Class<? extends IBaseBundle> bundleClass;
	private final TesterConfig myConfig;
	private volatile boolean dataUpdating = false;
	private volatile boolean stop = false;
	private String nextURL;
	private Map<String, Disease> LATEST = new ConcurrentHashMap<>();

	public RestApiAggregator(FhirContext ctx, Class<? extends IBaseResource> aClass, Class<? extends IBaseBundle> bundleClass, TesterConfig myConfig) {
		this.ctx = ctx;
		this.aClass = aClass;
		this.bundleClass = bundleClass;
		this.myConfig = myConfig;
	}

	public void stop() {
		this.stop = true;
	}

	public boolean isDataUpdating() {
		return dataUpdating;
	}

	@Override
	public boolean aggregateDirectSupported() {
		return false;
	}

	public Map<String, Disease> getCachedResult() {
		return LATEST;
	}

	@Override
	public Data aggregateDirect(int page, int size) {
		throw new UnsupportedOperationException();
	}

	public synchronized void aggregate() {
		try {
			dataUpdating = true;
			stop = false;
			Map<String, Disease> dataMap = new LinkedHashMap<>();
			String serverBase = myConfig.getIdToServerBase().entrySet().iterator().next().getValue();
			ctx.getRestfulClientFactory().setSocketTimeout(15 * 60 * 1000);
			ctx.getRestfulClientFactory().setConnectTimeout(15 * 60 * 1000);
			ctx.getRestfulClientFactory().setConnectionRequestTimeout(15 * 60 * 1000);
			IGenericClient client = ctx.newRestfulGenericClient(serverBase);
			BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(ServerSecurityInterceptor.INTERNAL_TOKEN);
			client.registerInterceptor(authInterceptor);
			IBaseBundle result = repeat(3, () ->
				client
					.search()
					.forResource(aClass)
					.count(1000)
					.returnBundle(bundleClass)
					.execute()
			);
			while (hasNext(result, dataMap)) {
				if (stop) throw new StopException();
				result = next();
				nextURL = null;
			}
			LATEST = dataMap;
		} catch (StopException e) {
			stop = false;
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
				IBaseCoding codingDt = c.getCode().getCoding().get(0);
				add(createDisease(codingDt), dataMap);
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
				IBaseCoding codingDt = c.getCode().getCoding().get(0);
				add(createDisease(codingDt), dataMap);
			});
		}
		return nextURL != null;
	}

	private Disease createDisease(IBaseCoding codingDt) {
		String code = codingDt.getCode();
		String system = codingDt.getSystem();
		String display = codingDt.getDisplay();
		return new Disease(system, code, display);
	}

	private void add(Disease disease, Map<String, Disease> dataMap) {
		dataMap.compute(disease.getCode(), (k, d) -> {
			if (d == null)
				d = disease;
			d.increment();
			return d;
		});
	}

	private IBaseBundle next() {
		String serverBase = myConfig.getIdToServerBase().entrySet().iterator().next().getValue();
		IGenericClient client = ctx.newRestfulGenericClient(serverBase);
		BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(ServerSecurityInterceptor.INTERNAL_TOKEN);
		client.registerInterceptor(authInterceptor);
		return repeat(3, () -> client
			.search()
			.byUrl(nextURL)
			.returnBundle(bundleClass)
			.execute());
	}

}
