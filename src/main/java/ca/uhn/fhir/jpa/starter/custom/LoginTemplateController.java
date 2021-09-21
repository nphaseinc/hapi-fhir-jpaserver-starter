package ca.uhn.fhir.jpa.starter.custom;

import ca.uhn.fhir.to.BaseController;
import ca.uhn.fhir.to.model.HomeRequest;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

import static org.apache.commons.lang3.StringUtils.defaultString;

@org.springframework.stereotype.Controller()
public class LoginTemplateController extends BaseController {
	@RequestMapping(value = {"/login"})
	public String actionLogin(HttpServletRequest theServletRequest, final HomeRequest theRequest, final ModelMap theModel) {
		final String serverId = theRequest.getServerIdWithDefault(myConfig);
		final String serverBase = theRequest.getServerBase(theServletRequest, myConfig);
		final String serverName = theRequest.getServerName(myConfig);
		final String apiKey = theRequest.getApiKey(theServletRequest, myConfig);
		theModel.put("serverId", sanitizeInput(serverId));
		theModel.put("baseName", sanitizeInput(serverName));
		theModel.put("apiKey", sanitizeInput(apiKey));
		theModel.put("resourceName", sanitizeInput(defaultString(theRequest.getResource())));
		theModel.put("encoding", sanitizeInput(theRequest.getEncoding()));
		theModel.put("pretty", sanitizeInput(theRequest.getPretty()));
		theModel.put("_summary", sanitizeInput(theRequest.get_summary()));
		theModel.put("serverEntries", myConfig.getIdToServerName());

		// doesn't need sanitizing
		theModel.put("base", serverBase);

		theModel.put("notHome", true);
		theModel.put("extraBreadcrumb", "Login");
		return "login";
	}


	private static String sanitizeInput(String theString) {
		String retVal = theString;
		if (retVal != null) {
			for (int i = 0; i < retVal.length(); i++) {
				char nextChar = retVal.charAt(i);
				switch (nextChar) {
					case '\'':
					case '"':
					case '<':
					case '>':
					case '&':
					case '/':
						retVal = retVal.replace(nextChar, '_');
				}
			}
		}
		return retVal;
	}
}
