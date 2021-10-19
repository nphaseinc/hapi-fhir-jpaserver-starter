package ca.uhn.fhir.jpa.starter.custom.aggregation.common;

import ca.uhn.fhir.jpa.starter.custom.aggregation.elastic.ElasticsearchAggregator;
import ca.uhn.fhir.jpa.starter.custom.aggregation.elastic.Indexer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.concurrent.TimeUnit;

@Component
public class AggregationListener {
	private final TaskScheduler taskScheduler = new ConcurrentTaskScheduler();
	private final Indexer indexer;
	private final DataAggregatorFactory aggregatorFactory;

	public AggregationListener(Indexer indexer, DataAggregatorFactory aggregatorFactory) {
		this.indexer = indexer;
		this.aggregatorFactory = aggregatorFactory;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void doSearchAfterStartup() {
		new Thread(() -> {
			waitPortAvailable(8080);
			StoppableDataAggregator dataAggregator = aggregatorFactory.getAggregator();
			if(dataAggregator.getClass() == ElasticsearchAggregator.class)
				indexer.doIndex();
			taskScheduler.schedule(() -> dataAggregator.aggregate(false), new PeriodicTrigger(1, TimeUnit.DAYS));
		}).start();
	}

	protected void waitPortAvailable(int port) {
		boolean result = false;
		while (!result) {
			try {
				Socket s = new Socket("127.0.0.1", port);
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
}
