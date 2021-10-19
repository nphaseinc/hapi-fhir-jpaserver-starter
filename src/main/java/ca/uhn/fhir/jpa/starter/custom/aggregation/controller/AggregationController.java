package ca.uhn.fhir.jpa.starter.custom.aggregation.controller;

import ca.uhn.fhir.jpa.starter.custom.ServerAdditionalEndpoints;
import ca.uhn.fhir.jpa.starter.custom.aggregation.common.DataAggregatorFactory;
import ca.uhn.fhir.jpa.starter.custom.aggregation.common.StoppableDataAggregator;
import ca.uhn.fhir.jpa.starter.custom.aggregation.dto.Data;
import ca.uhn.fhir.rest.annotation.Operation;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class AggregationController extends ServerAdditionalEndpoints {

	private final StoppableDataAggregator dataAggregator;

	public AggregationController(DataAggregatorFactory factory) {
		this.dataAggregator = factory.getAggregator();
	}

	@Operation(name = "$getSearchCondition", manualRequest = true, manualResponse = true, idempotent = true)
	public void getSearchCondition(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String params = new String(IOUtils.toByteArray(request.getInputStream()));
		Boolean nocache = getParam(params, "nocache", false, a -> Boolean.parseBoolean(a[1]));
		Boolean all = getParam(params, "all", false, a -> Boolean.parseBoolean(a[1]));
		Data ret;
		if (!all) {
			int page = getIntParam(params, "page", 0);
			int size = getIntParam(params, "size", 20);
			if (dataAggregator.aggregateDirectSupported())
				ret = dataAggregator.aggregateDirect(page, size);
			else {
				ret = dataAggregator.aggregate(page, size, !nocache);
			}
		} else {
			ret = dataAggregator.aggregate(nocache);
		}
		response(response, () -> ret);
	}

	@Operation(name = "$forceRegenerate", manualRequest = true, manualResponse = true)
	public void forceRegenerate(HttpServletResponse response) throws IOException {
		if (!dataAggregator.isDataUpdating())
			dataAggregator.aggregate(false);
		response.setContentType("application/json");
		String json = "{\"in-progress\" : \"" + dataAggregator.isDataUpdating() + "\"}";
		response.getWriter().write(json);
		response.getWriter().close();
	}

	@Operation(name = "$stopGeneration", manualRequest = true, manualResponse = true)
	public void stopGeneration(HttpServletResponse response) throws IOException {
		if (dataAggregator.isDataUpdating()) {
			dataAggregator.stop();
		}
		response.setContentType("application/json");
		String json = "{\"in-progress\" : \"stopping\"}";
		response.getWriter().write(json);
		response.getWriter().close();
	}
}
