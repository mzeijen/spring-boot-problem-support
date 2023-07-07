package com.example.demo;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.resource.ResourceWebHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotFoundTest {

	//******************************************************************************************************************
	//** WebMVC ********************************************************************************************************
	//******************************************************************************************************************

    @Nested
    class WebMvcTests {
        @Nested
        @TestPropertySource(
                properties = {
                        "spring.main.web-application-type=servlet",
                        "spring.mvc.problemdetails.enabled=true"
                }
        )
        class DefaultConfig extends Tests {
        }

        @Nested
        @TestPropertySource(
                properties = {
                        "spring.mvc.static-path-pattern=/static/**"
                }
        )
        class WithThrowingExceptionOnHandlerNotFound extends DefaultConfig {
        }

    }

    //******************************************************************************************************************
	//** Webflux *******************************************************************************************************
	//******************************************************************************************************************


    @Nested
    class WebfluxTests {
        @Nested
        @TestPropertySource(
                properties = {
                        "spring.main.web-application-type=reactive",
                        "spring.webflux.problemdetails.enabled=true",
                }
        )
        class DefaultConfig extends Tests {
        }

        @Nested
        @TestPropertySource(
                properties = {
                        "spring.webflux.static-path-pattern=/static/**"
                }
        )
        class WithoutResourceHandlerOnRoot extends DefaultConfig {
        }
    }

    //******************************************************************************************************************
    //** Tests *********************************************************************************************************
    //******************************************************************************************************************

    @SpringBootTest(
            classes = TestConfig.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
    )
    static abstract class Tests {

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
         * This test fails for both WebMVC and WebFlux when using the standard configuration, meaning the static
         * resource handler is configured to handle the {@code /**} path pattern.
         *
         * Reason:
         *  - For WebMVC the {@link ResourceHttpRequestHandler} handles this request because it is mapped on {@code /**}.
         *    It will not find any static resources for the non existing path, so it will then call the {@link HttpServletResponse#sendError(int)} with a 404 status.
         *    This bypasses the {@link ResponseEntityExceptionHandler} to handle the 404.
         *
         *    If we change the static resource path such that no handler is found for this request, and {@code spring.mvc.throw-exception-if-no-handler-found} is set not to {@code false},
         *    then this will result in a {@link NoHandlerFoundException} that will be handled by the {@link ResponseEntityExceptionHandler}.
         *
         *  - For Webflux the {@link ResourceWebHandler} does return a Mono with a {@link ResponseStatusException} error,
         *    but it doesn't get handled by the {@link ResponseEntityExceptionHandler}, but instead will be handled by the {@link DefaultErrorWebExceptionHandler}.
         *
         *    If we change the static resource path such that no handler is found for this request,
         *    then this will also result in a {@link ResponseStatusException}, but at an earlier stage where it will be handled by the {@link DispatcherHandler#handleDispatchError} method.
         *    This then ensures it does get handled by the {@link ResponseEntityExceptionHandler}.
         */
        @Test
        void should_return_404_problem_details_on_non_existing_path() {
            webTestClient.get()
                    .uri("/non-existing")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody(ProblemDetail.class).consumeWith(actual -> {
                        assertThat(actual.getResponseBody().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    });
        }

    }

    //******************************************************************************************************************
    //** Configuration *************************************************************************************************
    //******************************************************************************************************************

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {}

}
