package ca.uhn.fhir.jpa.starter.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ServerAdditionalEndpoints {

	protected <T> T getParam(String params, String name, T defaultVal, Function<? super String[], ? extends T> mapper) {
		try {
			Optional<T> o = Arrays.stream(params.split("&"))
				.map(s -> s.split("="))
				.filter(a -> a[0].equals(name))
				.findFirst()
				.map(mapper);
			if (o.isPresent()) return o.get();
		} catch (Exception ignore) {
		}
		return defaultVal;
	}

	protected int getIntParam(String params, String name, int defaultVal) {
		return getParam(params, name, defaultVal, a -> Integer.parseInt(a[1]));
	}

	protected LocalDateTime getDateTimeParam(String params, String name) {
		return getParam(params, name, null,
			a -> LocalDateTime.parse(URLDecoder.decode(a[1], StandardCharsets.UTF_8), DateTimeFormatter.ISO_OFFSET_DATE_TIME));
	}

	protected void response(HttpServletResponse response, Supplier<?> dataSupplier) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		response.setContentType("application/json");
		objectMapper.writeValue(response.getWriter(), dataSupplier.get());
		response.getWriter().close();
	}
}
