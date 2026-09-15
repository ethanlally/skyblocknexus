package com.ethanlally.skyblocknexus.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class UpstreamClientsTests {

    @Test
    void productionClientsHaveConnectAndReadTimeouts() {
        RestClient client = UpstreamClients.create("http://localhost");
        Object factory = ReflectionTestUtils.getField(client, "clientRequestFactory");
        assertThat(factory).isInstanceOf(JdkClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout"))
                .isEqualTo(Duration.ofSeconds(10));
        HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(factory, "httpClient");
        assertThat(httpClient).isNotNull();
        assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(5));
    }

    @Test
    @Timeout(10)
    void anUnresponsiveServerActuallyTimesOut() throws Exception {
        CountDownLatch releaseResponse = new CountDownLatch(1);
        CountDownLatch requestReceived = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            requestReceived.countDown();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            RestClient client = RestClient.builder()
                    .requestFactory(UpstreamClients.requestFactory(Duration.ofMillis(200)))
                    .build();
            assertThatThrownBy(() -> client.get()
                    .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/slow")
                    .retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class)
                    .hasCauseInstanceOf(HttpTimeoutException.class);
            assertThat(requestReceived.getCount()).isZero();
        } finally {
            releaseResponse.countDown();
            server.stop(0);
        }
    }
}
