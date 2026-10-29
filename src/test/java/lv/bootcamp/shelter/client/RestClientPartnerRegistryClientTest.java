package lv.bootcamp.shelter.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientPartnerRegistryClientTest {

    private MockWebServer server;
    private RestClientPartnerRegistryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new RestClientPartnerRegistryClient(
                RestClient.builder(), "http://localhost:" + server.getPort());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void lookup_shouldParseSuccessResponseAndCallCorrectPath() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"registered": true, "partnerShelterName": "Riga Shelter"}
                        """));

        PartnerRegistryResult result = client.lookup("CHIP-123");

        assertThat(result.registered()).isTrue();
        assertThat(result.partnerShelterName()).isEqualTo("Riga Shelter");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/registry/CHIP-123");
    }

    @Test
    void lookup_shouldThrowWhenPartnerFails() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.lookup("CHIP-123"))
                .isInstanceOf(RestClientResponseException.class)
                .extracting(e -> ((RestClientResponseException) e).getStatusCode().value())
                .isEqualTo(500);
    }
}