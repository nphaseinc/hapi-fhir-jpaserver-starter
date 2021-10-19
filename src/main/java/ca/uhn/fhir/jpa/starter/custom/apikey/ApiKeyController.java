package ca.uhn.fhir.jpa.starter.custom.apikey;

import ca.uhn.fhir.jpa.starter.custom.ServerAdditionalEndpoints;
import ca.uhn.fhir.rest.annotation.Operation;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ApiKeyController extends ServerAdditionalEndpoints {
	private final ApiKeyService apiKeyService;

	public ApiKeyController(ApiKeyService apiKeyService) {
		this.apiKeyService = apiKeyService;
	}

	@Operation(name = "$generateApiKey", manualRequest = true, manualResponse = true )
	public void generateApiKey(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String params = new String(IOUtils.toByteArray(request.getInputStream()));
		LocalDateTime expireDate = getDateTimeParam(params, "expireDate");
		response(response, () -> apiKeyService.generateNewKey(expireDate));
	}

	@Operation(name = "$revokeKey", manualRequest = true, manualResponse = true )
	public void revokeKey(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String params = new String(IOUtils.toByteArray(request.getInputStream()));
		int id = getIntParam(params, "id", 0);
		if(id == 0) {
			throw new IllegalArgumentException("id is required");
		}
		response(response, () -> {apiKeyService.revokeKey(id); return "";});
	}

	@Operation(name = "$getApiKeys", manualRequest = true, manualResponse = true, idempotent = true)
	public void getApiKeys(HttpServletResponse response) throws IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		response(response, () -> apiKeyService.getAllKeys(authentication));
	}

	@Operation(name = "$switchStatus", manualRequest = true, manualResponse = true )
	public void switchStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String params = new String(IOUtils.toByteArray(request.getInputStream()));
		int id = getIntParam(params, "id", 0);
		if(id == 0) {
			throw new IllegalArgumentException("id is required");
		}
		response(response, () -> apiKeyService.switchStatus(id));
	}
}
