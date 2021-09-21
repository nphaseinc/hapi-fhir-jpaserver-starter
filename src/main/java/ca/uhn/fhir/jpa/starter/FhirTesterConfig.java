package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.starter.custom.RCCAuthenticationProvider;
import ca.uhn.fhir.to.FhirTesterMvcConfig;
import ca.uhn.fhir.to.TesterConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

//@formatter:off
/**
 * This spring config file configures the web testing module. It serves two
 * purposes:
 * 1. It imports FhirTesterMvcConfig, which is the spring config for the
 *    tester itself
 * 2. It tells the tester which server(s) to talk to, via the testerConfig()
 *    method below
 */
@Configuration
@Import(FhirTesterMvcConfig.class)
@ComponentScan(basePackages = "ca.uhn.fhir.jpa.starter.custom")
public class FhirTesterConfig {

	/**
	 * This bean tells the testing webpage which servers it should configure itself
	 * to communicate with. In this example we configure it to talk to the local
	 * server, as well as one public server. If you are creating a project to
	 * deploy somewhere else, you might choose to only put your own server's
	 * address here.
	 *
	 * Note the use of the ${serverBase} variable below. This will be replaced with
	 * the base URL as reported by the server itself. Often for a simple Tomcat
	 * (or other container) installation, this will end up being something
	 * like "http://localhost:8080/hapi-fhir-jpaserver-starter". If you are
	 * deploying your server to a place with a fully qualified domain name,
	 * you might want to use that instead of using the variable.
	 */
  @Bean
  public TesterConfig testerConfig(AppProperties appProperties) {
    TesterConfig retVal = new TesterConfig();
    appProperties.getTester().entrySet().stream().forEach(t -> {
      retVal
        .addServer()
        .withId(t.getKey())
        .withFhirVersion(t.getValue().getFhir_version())
        .withBaseUrl(t.getValue().getServer_address())
        .withName(t.getValue().getName());
      retVal.setRefuseToFetchThirdPartyUrls(
        t.getValue().getRefuse_to_fetch_third_party_urls());

    });
    return retVal;
  }

	@Configuration
	@EnableWebSecurity
	public static class SecurityConfig extends WebSecurityConfigurerAdapter {

		@Autowired
		private RCCAuthenticationProvider authProvider;

		@Override
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			auth.authenticationProvider(authProvider);
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			String[] staticResources = {
				"/css/**",
				"/img/**",
				"/fonts/**",
				"/js/**",
				"/resources/**",
			};
			http
				.csrf().disable()
				.authorizeRequests()
				.antMatchers(staticResources).permitAll()
				.antMatchers("/login").permitAll()
				.antMatchers("/fhir/**").permitAll()
				.antMatchers("/resource**").permitAll()
				.antMatchers("/conformance**").permitAll()
				.anyRequest().authenticated()
				.and()
				.formLogin()
				.loginPage("/login")
				.loginProcessingUrl("/login")
				.defaultSuccessUrl("/", true)
				//.failureUrl("/login?error=true")
				.failureHandler(authenticationFailureHandler())
				.and()
				.logout()
				.logoutUrl("/logout")
				.deleteCookies("JSESSIONID")
				.logoutSuccessHandler(logoutSuccessHandler());
		}

		private LogoutSuccessHandler logoutSuccessHandler() {
			return new SimpleUrlLogoutSuccessHandler() {
				@Override
				public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
					final String refererUrl = request.getHeader("Referer");
					System.out.println(refererUrl);
					super.onLogoutSuccess(request, response, authentication);
				}
			};
		}

		private AuthenticationFailureHandler authenticationFailureHandler() {
			return (httpServletRequest, httpServletResponse, e) -> {
				//httpServletResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
				//String jsonPayload = "{\"message\" : \"%s\", \"timestamp\" : \"%s\" }";
				//httpServletResponse.getOutputStream().println(String.format(jsonPayload, e.getMessage(), Calendar.getInstance().getTime()));
				httpServletResponse.sendRedirect("/login?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
			};
		}
	}

}
//@formatter:on
