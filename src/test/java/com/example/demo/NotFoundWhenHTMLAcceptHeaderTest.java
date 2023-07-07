package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotFoundWhenHTMLAcceptHeaderTest {

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
        class WithoutResourceHandlerOnRoot extends DefaultConfig {
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
         * Fails when the resource handlers are not configured to handle the path that does not exist.
         * At that point you always get a JSON back. Although this can be seen as the correct behaviour,
         * this is not very human friendly when you work on a system that servers both HTML pages and has a REST API.
         */
        @Test
        void should_return_404_error_page_for_text_html_on_non_existing_path() {
            webTestClient.get()
                    .uri("/non-existing")
                    .accept(MediaType.TEXT_HTML)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                    .expectBody(String.class).consumeWith(actual -> {
                        assertThat(actual.getResponseBody()).contains("Whitelabel Error Page");
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
