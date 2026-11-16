package com.glea.weather.aemet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class AemetTransportTest {

    @Test
    void doesNotFollowLoopbackRedirects() throws IOException {
        HttpServer server = loopbackServer();
        AtomicInteger targetRequests = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            targetRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            RestClient client = new AemetClientConfiguration().aemetRestClientBuilder().build();
            client.get().uri(uri(server, "/redirect")).retrieve().toBodilessEntity();

            assertEquals(0, targetRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readTimeoutBoundsLoopbackResponseWait() throws IOException {
        HttpServer server = loopbackServer();
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            handlerStarted.countDown();
            try {
                releaseHandler.await(4, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client is expected to close the timed-out loopback request.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            RestClient client = new AemetClientConfiguration().aemetRestClientBuilder().build();
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                assertThrows(RestClientException.class,
                        () -> client.get().uri(uri(server, "/slow")).retrieve().body(String.class));
                if (!handlerStarted.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("Loopback handler did not receive the request.");
                }
            });
        } finally {
            releaseHandler.countDown();
            server.stop(0);
        }
    }

    @Test
    void connectionRefusalIsBoundedAndReportedAsTransportFailure() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        RestClient client = new AemetClientConfiguration().aemetRestClientBuilder().build();

        assertTimeoutPreemptively(Duration.ofSeconds(4), () -> assertThrows(RestClientException.class,
                () -> client.get().uri(URI.create("http://127.0.0.1:" + closedPort + "/refused"))
                        .retrieve().body(String.class)));
    }

    private static HttpServer loopbackServer() throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    }

    private static URI uri(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
