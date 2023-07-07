package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;


class ResponseEntityExceptionHandlerOrderingTest {

	//******************************************************************************************************************
	//** WebMVC ********************************************************************************************************
	//******************************************************************************************************************

	@Nested
	class WebMvcTests {
		@Nested
		@SpringBootTest(
				classes = {
						TestConfig.class,
						CatchAllWebMvcControllerAdvice.class
				},
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = {
						"spring.main.web-application-type=servlet",
						"spring.mvc.problemdetails.enabled=true",
				}
		)
		class WebMvcWithProblemDetailsExceptionHandler extends AllTests {
		}

		@Nested
		@SpringBootTest(
				classes = {
						TestConfig.class,
						CatchAllWebMvcControllerAdvice.class,
						OrderedProblemDetailsExceptionHandler.class
				},
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = {
						"spring.main.web-application-type=servlet",
						"spring.mvc.problemdetails.enabled=true",
				}
		)
		class WebMvcWithCustomOrderedProblemDetailsExceptionHandler extends AllTests {
		}

		@ControllerAdvice
		@Order(Ordered.LOWEST_PRECEDENCE)
		static class CatchAllWebMvcControllerAdvice {

			@ExceptionHandler
			public ResponseEntity<ProblemDetail> convertToProblem(Exception ex, WebRequest request) {
				ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal exception");
				problemDetail.setProperty("from-catch-all", true);

				return new ResponseEntity<>(problemDetail, HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		@ControllerAdvice
		@Order(Ordered.LOWEST_PRECEDENCE - 1)  // Needs to have a higher order (thus a lower number) then the CatchAllWebMvcControllerAdvice
		static class OrderedProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {}


	}

	//******************************************************************************************************************
	//** Webflux *******************************************************************************************************
	//******************************************************************************************************************

	@Nested
	class WebfluxTests {
		@Nested
		@SpringBootTest(
				classes = {
						TestConfig.class,
						CatchAllWebfluxControllerAdvice.class
				},
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = {
						"spring.main.web-application-type=reactive",
						"spring.webflux.problemdetails.enabled=true",
				}
		)
		class WebfluxWithProblemDetailsExceptionHandler extends AllTests {
		}

		@Nested
		@SpringBootTest(
				classes = {
						TestConfig.class,
						CatchAllWebfluxControllerAdvice.class,
						OrderedProblemDetailsExceptionHandler.class
				},
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = {
						"spring.main.web-application-type=reactive",
						"spring.webflux.problemdetails.enabled=true",
				}
		)
		class WebfluxWithCustomOrderedProblemDetailsExceptionHandler extends AllTests {
		}

		@ControllerAdvice
		@Order(Ordered.LOWEST_PRECEDENCE)
		static class CatchAllWebfluxControllerAdvice {

			@ExceptionHandler
			public Mono<ResponseEntity<Object>> convertToProblem(Exception ex, ServerWebExchange exchange) {
				ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal exception");
				problemDetail.setProperty("from-catch-all", true);

				return Mono.just(new ResponseEntity<>(problemDetail, HttpStatus.INTERNAL_SERVER_ERROR));
			}
		}

		@ControllerAdvice
		@Order(Ordered.LOWEST_PRECEDENCE - 1) // Needs to have a higher order (thus a lower number) then the CatchAllWebfluxControllerAdvice
		static class OrderedProblemDetailsExceptionHandler extends org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler {}

	}

	//******************************************************************************************************************
	//** Tests *********************************************************************************************************
	//******************************************************************************************************************


	static abstract class AllTests  {

		@LocalServerPort
		int port;

		WebTestClient webTestClient;

		@BeforeEach
		void initWebClient() {
			webTestClient = WebTestClient.bindToServer()
					.baseUrl("http://localhost:" + port)
					.responseTimeout(Duration.ofMinutes(10)) // To be able to do debugging
					.build();
		}

		/**
		 * This test fails if we do not define a custom ResponseEntityExceptionHandler bean with an order that is higher than the lowest precedence
		 */
		@Test
		void should_return_problem_when_using_wrong_http_method() {
			webTestClient.delete()
					.uri("/")
					.accept(MediaType.APPLICATION_JSON)
					.exchange()
					.expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
					.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
					.expectBody(ProblemDetail.class).consumeWith(actual -> {
						assertThat(actual.getResponseBody().getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
					});
		}

		/**
		 * This test fails if we do not define a custom ResponseEntityExceptionHandler bean with an order that is higher than the lowest precedence
		 */
		@Test
		void should_return_thrown_error_response() {
			webTestClient.get()
					.uri("/throws-a-problem")
					.accept(MediaType.APPLICATION_JSON)
					.exchange()
					.expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
					.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
					.expectBody(ProblemDetail.class).consumeWith(actual -> {
						assertThat(actual.getResponseBody().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
					});
		}

		/**
		 * This test succeeds in all cases, but it is here to show that the catch-all @ExceptionHandler does work
		 */
		@Test
		void should_return_internal_server_error_from_catch_all() {
			webTestClient.get()
					.uri("/throws-an-exception")
					.accept(MediaType.APPLICATION_JSON)
					.exchange()
					.expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
					.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
					.expectBody(ProblemDetail.class).consumeWith(actual -> {
						ProblemDetail problem = actual.getResponseBody();

						assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
						assertThat(problem.getProperties().get("from-catch-all")).isEqualTo(true);
					});
		}
	}

	//******************************************************************************************************************
	//** Application setup *********************************************************************************************
	//******************************************************************************************************************

	@Configuration
	@EnableAutoConfiguration
	@Import({
			ExampleRestController.class
	})
	static class TestConfig {}

	@RestController
	static class ExampleRestController {

		@GetMapping(path = "/", produces = MediaType.APPLICATION_JSON_VALUE)
		public String get() {
			return "{}";
		}

		@GetMapping(path = "/throws-a-problem", produces = MediaType.APPLICATION_JSON_VALUE)
		public String throwsAJsonProblem() {
			throw new ErrorResponseException(HttpStatus.BAD_REQUEST, new RuntimeException("a problem"));
		}

		@GetMapping(path = "/throws-an-exception", produces = MediaType.APPLICATION_JSON_VALUE)
		public String throwsAnException() {
			throw new RuntimeException("Something happened");
		}

	}
}
