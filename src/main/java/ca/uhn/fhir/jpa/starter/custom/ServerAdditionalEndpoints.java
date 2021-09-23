package ca.uhn.fhir.jpa.starter.custom;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.to.TesterConfig;
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
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@RestController
public class ServerAdditionalEndpoints {

	private final TaskScheduler taskScheduler = new ConcurrentTaskScheduler();

	@Autowired
	protected TesterConfig myConfig;

	private static IBaseBundle result;
	private static LocalDateTime resultDate;
	private static ConditionSearch conditionSearch;


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
			taskScheduler.schedule(conditionSearch, new PeriodicTrigger(1, TimeUnit.HOURS));
		}).start();
	}

	@Operation(name = "$getSearchCondition", manualRequest = true, type = IBaseBundle.class)
	public IBaseBundle getSearchCondition() {
		if (result == null && !conditionSearch.inProgress) {
			doSearchCondition();
		}
		return result;
	}

	public void doSearchCondition() {
		new Thread(conditionSearch).start();
	}

	@Operation(name = "$searchConditionDone", manualRequest = true, manualResponse = true)
	public void searchConditionDone(HttpServletResponse response) throws IOException {
		if (result == null && !conditionSearch.inProgress) {
			doSearchCondition();
		}
		response.setContentType("application/json");
		long between = resultDate != null ? ChronoUnit.MILLIS.between(resultDate, LocalDateTime.now()) : 0;
		String json = "{\"in-progress\" : \"" + conditionSearch.inProgress + "\", "
			+ "\"result-exist\" : \"" + (result != null) + "\"" +
			(result != null ? ", \"result-age\" : \"" + between + "\"" : "") +
			"}";
		response.getWriter().write(json);
		response.getWriter().close();
	}


	private final class ConditionSearch implements Runnable {
		private final FhirContext ctx;
		private final Class<? extends IBaseResource> aClass;
		private final Class<? extends IBaseBundle> bundleClass;
		private volatile boolean inProgress = false;

		public ConditionSearch(FhirContext ctx, Class<? extends IBaseResource> aClass, Class<? extends IBaseBundle> bundleClass) {
			this.ctx = ctx;
			this.aClass = aClass;
			this.bundleClass = bundleClass;
		}

		@Override
		public void run() {
			try {
				inProgress = true;
				String serverBase = myConfig.getIdToServerBase().entrySet().iterator().next().getValue();
				IGenericClient client = ctx.newRestfulGenericClient(serverBase);
				ctx.getRestfulClientFactory().setSocketTimeout(15* 60 * 1000);
				result = client
					.search()
					.forResource(aClass)
					.sort(new SortSpec("code"))
					.count(100)
					.returnBundle(bundleClass)
					.execute();
				resultDate = LocalDateTime.now();
			} finally {
				inProgress = false;
			}
		}
	}
}
