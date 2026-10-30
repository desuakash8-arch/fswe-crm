package com.klu.crmbackend.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResendEmailSenderTests {
    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private String requestBody(HttpRequest request) {
        var result = new CompletableFuture<String>();
        var buffer = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer bytes) { byte[] data = new byte[bytes.remaining()]; bytes.get(data); buffer.writeBytes(data); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(buffer.toString(StandardCharsets.UTF_8)); }
        });
        return result.join();
    }

    @Test void sendsViaHttpsWithDemoRecipientAndNeverCallsSmtp() throws Exception {
        var http = mock(HttpClient.class);
        var accepted = response(200, "{\"id\":\"mail-test-reference\"}");
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(accepted);
        var smtp = mock(JavaMailSender.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("smtp", smtp);
        var service = new EmailService(beans.getBeanProvider(JavaMailSender.class), new ResendEmailSender("re_test_only", http));
        ReflectionTestUtils.setField(service, "provider", "resend");
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "onboarding@resend.dev");
        ReflectionTestUtils.setField(service, "recipientOverride", "owner@test.invalid");
        assertTrue(service.sendEmail("fake-customer@test.invalid", "Invoice", "Pay at https://crm.test/pay#private-token").sent());
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        var request = captor.getValue();
        assertEquals("https://api.resend.com/emails", request.uri().toString());
        assertEquals("Bearer re_test_only", request.headers().firstValue("Authorization").orElseThrow());
        var json = JsonMapper.builder().build().readTree(requestBody(request));
        assertEquals(1, json.path("to").size());
        assertEquals("owner@test.invalid", json.path("to").get(0).asString());
        assertEquals("[LOCAL TEST] Invoice", json.path("subject").asString());
        assertTrue(json.path("text").asString().contains("https://crm.test/pay#private-token"));
        verifyNoInteractions(smtp);
    }

    @Test void missingKeyNeverMakesRequest() {
        var http = mock(HttpClient.class);
        assertFalse(new ResendEmailSender("", http).send("from@test.invalid", "to@test.invalid", "Test", "Body").sent());
        verifyNoInteractions(http);
    }

    @Test void providerFailuresRemainRetryableWithoutExposingResponseBodies() throws Exception {
        var http = mock(HttpClient.class);
        var sender = new ResendEmailSender("re_test_only", http);
        for (int code : new int[]{401, 403, 429, 500}) {
            var rejected = response(code, "private provider error detail");
            when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(rejected);
            var result = sender.send("from@test.invalid", "to@test.invalid", "Test", "Body");
            assertFalse(result.sent());
            assertFalse(result.message().contains("private provider error detail"));
        }
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenThrow(new java.io.IOException("private network detail"));
        assertFalse(sender.send("from@test.invalid", "to@test.invalid", "Test", "Body").sent());
    }

    @Test void successfulHttpResponseMustIncludeProviderReference() throws Exception {
        var http = mock(HttpClient.class);
        var incomplete = response(200, "{}");
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(incomplete);
        assertFalse(new ResendEmailSender("re_test_only", http).send("from@test.invalid", "to@test.invalid", "Test", "Body").sent());
    }
}
