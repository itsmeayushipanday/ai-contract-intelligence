package com.contractai.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Real HTTP against a real embedded Tomcat (MockMvc cannot show this): an oversized upload must come
 * back as a readable 413, not as a dropped connection. A dropped connection is what a browser
 * reports as "Failed to fetch", hiding our error message.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LargeUploadHttpTest {

    @LocalServerPort
    private int port;

    private HttpResponse<String> upload(int fileMegabytes) throws Exception {
        String boundary = "----test" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"big.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n%PDF-1.4").getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[fileMegabytes * 1024 * 1024]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/contracts"))
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void oversizedUploadGetsReadable413WithOurMessage() throws Exception {
        HttpResponse<String> response = upload(12); // limit is 10 MB

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"title\":\"File too large\"");
        assertThat(response.body()).contains("10 MB");
    }

    @Test
    void muchLargerUploadAlsoGetsReadable413() throws Exception {
        HttpResponse<String> response = upload(60);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"title\":\"File too large\"");
    }
}
