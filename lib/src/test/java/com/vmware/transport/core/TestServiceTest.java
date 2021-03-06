/*
 * Copyright 2018-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.vmware.transport.bridge.Response;
import com.vmware.transport.bus.EventBus;
import com.vmware.transport.bus.EventBusImpl;
import com.vmware.transport.bus.model.Message;
import com.vmware.transport.bus.store.BusStoreApi;
import com.vmware.transport.bus.store.StoreManager;
import com.vmware.transport.core.error.GeneralError;
import com.vmware.transport.core.error.RestError;
import com.vmware.transport.core.model.*;
import com.vmware.transport.core.operations.MockRestController;
import com.vmware.transport.core.operations.RestService;
import com.vmware.transport.core.operations.SecurityConfiguration;
import com.vmware.transport.core.util.RestControllerInvoker;
import com.vmware.transport.core.util.RestControllerReflection;
import com.vmware.transport.core.util.URIMatcher;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = {
        SecurityConfiguration.class,
        RestService.class,
        MockRestController.class,
        RestControllerInvoker.class,
        DefaultParameterNameDiscoverer.class,
        RestControllerReflection.class,
        URIMatcher.class,
        RestTemplate.class,
        TestService.class,
        EventBusImpl.class,
        RestService.class,
        StoreManager.class
})
public class TestServiceTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(9999));

    @Autowired
    EventBus bus;

    @Autowired
    BusStoreApi storeManager;

    @Autowired
    ApplicationContext context;

    @Test
    public void checkChannelIsSet() {

        TestService service = context.getBean(TestService.class);
        Assert.assertEquals("test::TestService", service.getServiceChannel());

    }


    @Test
    public void testServiceCommandA() {

        String serviceChannel = "test::TestService";

        TestServiceObjectRequest requestPayload = new TestServiceObjectRequest();
        requestPayload.setRequestValue("My Little Melody");

        TestRequest request = new TestRequest();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setRequest(TestCommand.COMMAND_A);
        request.setPayload(requestPayload);

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    TestResponse resp = (TestResponse) msg.getPayload();
                    TestServiceObjectResponse respPayload = (TestServiceObjectResponse) resp.getPayload();
                    Assert.assertEquals(id, resp.getId());
                    Assert.assertEquals("CommandA-My Little Melody", respPayload.getResponseValue());
                },
                (Message error) -> {
                    Assert.fail();
                }
        );
    }

    @Test
    public void testServiceCommandB() {

        String serviceChannel = "test::TestService";

        TestServiceObjectRequest requestPayload = new TestServiceObjectRequest();
        requestPayload.setRequestValue("My Little Song");

        TestRequest request = new TestRequest();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setRequest(TestCommand.COMMAND_B);
        request.setPayload(requestPayload);

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    TestResponse resp = (TestResponse) msg.getPayload();
                    TestServiceObjectResponse respPayload = (TestServiceObjectResponse) resp.getPayload();
                    Assert.assertEquals(id, resp.getId());
                    Assert.assertEquals("CommandB-My Little Song", respPayload.getResponseValue());
                }
        );
    }


    @Test
    public void testErrorHandlingWithBadPayload() {

        String serviceChannel = "test::TestService";

        bus.requestOnce(
                serviceChannel,
                "Some String",
                (Message msg) -> {
                    // should not fire
                    Assert.fail();
                },
                (Message msg) -> {
                    GeneralError error = (GeneralError) msg.getPayload();
                    Assert.assertEquals("TestService cannot handle request, "
                            + "payload isn't derived from 'Request', type: String", error.message);
                }
        );
    }

    @Test
    public void testServiceCommandC() throws Exception {

        stubFor(get(urlEqualTo("/bus-test-service"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                        .withBody("pretty-baby")));


        String serviceChannel = "test::TestService";

        TestServiceObjectRequest requestPayload = new TestServiceObjectRequest();
        requestPayload.setRequestValue("Happy Little Melody");

        TestRequest request = new TestRequest();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setRequest(TestCommand.COMMAND_C);
        request.setPayload(requestPayload);
        request.uri = new URI("http://localhost:9999/bus-test-service");
        request.method = HttpMethod.GET;

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    TestResponse resp = (TestResponse) msg.getPayload();
                    TestServiceObjectResponse respPayload = (TestServiceObjectResponse) resp.getPayload();
                    Assert.assertEquals(id, resp.getId());
                    Assert.assertEquals("\"pretty-baby\"", respPayload.getResponseValue());
                }
        );
    }

    @Test
    public void testServiceCommandWithError() throws Exception {

        stubFor(get(urlEqualTo("/bus-test-service-error"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.FORBIDDEN.value())
                        .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                        .withBody("{message: 'no-access!'}")));


        String serviceChannel = "test::TestService";

        TestServiceObjectRequest requestPayload = new TestServiceObjectRequest();
        requestPayload.setRequestValue("Happy Little Melody");

        TestRequest request = new TestRequest();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setRequest(TestCommand.COMMAND_C);
        request.setPayload(requestPayload);
        request.uri = new URI("http://localhost:9999/bus-test-service-error");
        request.method = HttpMethod.GET;

        bus.requestOnceWithId(
                id,
                serviceChannel,
                request,
                (Message msg) -> {
                    Assert.fail();
                },
                (Message msg) -> {
                    Response<RestError> resp = (Response<RestError>) msg.getPayload();
                    RestError error = resp.getPayload();
                    Assert.assertEquals(new Integer(403), error.errorCode);
                    Assert.assertEquals("REST Client Error, unable to complete request: 403 Forbidden", error.message);
                }
        );
    }

    @Test
    public void testServiceCommandOverQueue() {

        String serviceChannel = "test::TestService";

        TestServiceObjectRequest requestPayload = new TestServiceObjectRequest();
        requestPayload.setRequestValue("My Little Song");

        TestRequest request = new TestRequest();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setRequest(TestCommand.COMMAND_OVERQUEUE);
        request.setTargetUser("user-id");
        request.setPayload(requestPayload);

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    TestResponse resp = (TestResponse) msg.getPayload();
                    TestServiceObjectResponse respPayload = (TestServiceObjectResponse) resp.getPayload();
                    Assert.assertEquals(id, resp.getId());
                    Assert.assertEquals("CommandOverQueue-My Little Song", respPayload.getResponseValue());
                }
        );
    }

    @Test
    public void testServiceCommandOverQueueWithErrorResponse() {
        String serviceChannel = "test::TestService";

        TestServiceObjectRequest requestPayload = new TestServiceObjectRequest();
        requestPayload.setRequestValue("My Little Song");

        TestRequest request = new TestRequest();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setRequest(TestCommand.ERROR_RESPONSE_OVERQUEUE);
        request.setTargetUser("user-id");
        request.setPayload(requestPayload);

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    Assert.fail();
                },
                (Message msg) -> {
                    Response<GeneralError> resp = (Response<GeneralError>) msg.getPayload();
                    GeneralError error = resp.getPayload();
                    Assert.assertEquals("user-id", msg.getTargetUser());
                    Assert.assertEquals("error", error.message);
                }
        );
    }

    @Test
    public void testServiceCommandOverQueueWithGeneralError() {
        String serviceChannel = "test::TestService";

        TestServiceObjectRequest requestPayload = new TestServiceObjectRequest();
        requestPayload.setRequestValue("My Little Song");

        TestRequest request = new TestRequest();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setRequest(TestCommand.GENERAL_ERROR_OVERQUEUE);
        request.setTargetUser("user-id");
        request.setPayload(requestPayload);

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    Assert.fail();
                },
                (Message msg) -> {
                    Response<GeneralError> resp = (Response<GeneralError>) msg.getPayload();
                    GeneralError error = resp.getPayload();
                    Assert.assertEquals("user-id", msg.getTargetUser());
                    Assert.assertEquals("I'm a teapot", error.message);
                }
        );
    }

    @Test
    public void testRejectedRequest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectWriter ow = objectMapper.writer().withDefaultPrettyPrinter();
        String serviceChannel = "test::TestService";

        TestRequest request = new TestRequest();
        GeneralError error = new GeneralError();
        error.message = "Bad request";
        error.errorCode = 400;

        request.setRequest(TestCommand.COMMAND_B);
        request.setIsRejected(true);
        request.setId(UUID.randomUUID());
        request.setPayload(ow.writeValueAsString(error));

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    Assert.fail();
                },
                (Message msg) -> {
                    Response<GeneralError> resp = (Response<GeneralError>) msg.getPayload();
                    GeneralError generalError = resp.getPayload();
                    Assert.assertEquals("Bad request", generalError.message);
                    Assert.assertEquals(400, (long) generalError.errorCode);
                }
        );
    }

    @Test
    public void testRejectedRequestWithError() {
        String serviceChannel = "test::TestService";

        TestRequest request = new TestRequest();
        GeneralError error = new GeneralError();

        request.setRequest(TestCommand.COMMAND_B);
        request.setIsRejected(true);
        request.setId(UUID.randomUUID());
        request.setPayload(error);

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    Assert.fail();
                },
                (Message msg) -> {
                    Response<GeneralError> resp = (Response<GeneralError>) msg.getPayload();
                    GeneralError generalError = resp.getPayload();
                    Assert.assertTrue(generalError.message.contains("Failed to parse request payload into GeneralError:"));
                    Assert.assertEquals(500, (long) generalError.errorCode);
                }
        );
    }

    @Test
    public void testRejectedRequestWithErrorToTarget() {
        String serviceChannel = "test::TestService";

        TestRequest request = new TestRequest();
        GeneralError error = new GeneralError();

        request.setRequest(TestCommand.COMMAND_B);
        request.setIsRejected(true);
        request.setTargetUser("user-id");
        request.setId(UUID.randomUUID());
        request.setPayload(error);

        bus.requestOnce(
                serviceChannel,
                request,
                (Message msg) -> {
                    Assert.fail();
                },
                (Message msg) -> {
                    Response<GeneralError> resp = (Response<GeneralError>) msg.getPayload();
                    GeneralError generalError = resp.getPayload();
                    Assert.assertTrue(generalError.message.contains("Failed to parse request payload into GeneralError:"));
                    Assert.assertEquals(500, (long) generalError.errorCode);
                    Assert.assertEquals("user-id", msg.getTargetUser());
                }
        );
    }

    @Test
    public void testServiceWideHeaders() throws Exception {

        stubFor(get(urlEqualTo("/header-2"))
                .withHeader("X-The-Number", matching("42"))
                .willReturn(aResponse().withStatus(org.apache.http.HttpStatus.SC_OK)));

        TestService service = context.getBean(TestService.class);
        Map<String, String> map = new HashMap<>();
        map.put("X-The-Number", "42");

        service.setHeaders(map);

        String serviceChannel = "test::TestService";

        TestServiceObjectRequest requestPayload = new TestServiceObjectRequest();
        requestPayload.setRequestValue("Happy Little Melody");

        TestRequest request = new TestRequest();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setRequest(TestCommand.COMMAND_C);
        request.setPayload(requestPayload);
        request.uri = new URI("http://localhost:9999/header-2");
        request.method = HttpMethod.GET;

        bus.requestOnceWithId(
                id,
                serviceChannel,
                request,
                (Message msg) -> {
                    System.out.println(msg.toString());
                },
                (Message msg) -> {
                    Assert.fail();
                }
        );
    }
}
