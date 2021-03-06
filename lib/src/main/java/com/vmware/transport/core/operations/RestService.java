/*
 * Copyright 2018-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.core.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.vmware.transport.bridge.Request;
import com.vmware.transport.bridge.Response;
import com.vmware.transport.bus.model.Message;
import com.vmware.transport.bus.store.model.BusStore;
import com.vmware.transport.core.AbstractService;
import com.vmware.transport.core.CoreChannels;
import com.vmware.transport.core.CoreStoreKeys;
import com.vmware.transport.core.CoreStores;
import com.vmware.transport.core.model.RestServiceRequest;
import com.vmware.transport.core.error.RestError;
import com.vmware.transport.core.model.RestOperation;
import com.vmware.transport.core.util.ClassMapper;
import com.vmware.transport.core.util.RestControllerInvoker;
import com.vmware.transport.core.util.URIMatcher;
import com.vmware.transport.core.util.URIMethodResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * RestService is responsible for handling UI Rest requests. It operates in two modes:
 *
 * 1. As a simple REST client that translates a RestOperation object into a Rest Call to an external URI
 * 2. As a dispatch engine that checks if there are any RestController instances that serve the requested URI.
 * If there is a match, the method arguments are extracted from all the meta data provided via annotations to the
 * method, and then calls the method using the correct sequence of arguments, with the correct types, in the correct
 * order.
 *
 *
 * @see com.vmware.transport.core.model.RestOperation
 */
@Service
@SuppressWarnings("unchecked")
public class RestService extends AbstractService<Request<RestServiceRequest>, Response> {
    private final URIMatcher uriMatcher;
    private final RestControllerInvoker controllerInvoker;
    private final ObjectMapper mapper;
    private BusStore<String, String> baseHostStore;
    private JsonParser parser;

    @Autowired
    public RestService(URIMatcher uriMatcher, RestControllerInvoker controllerInvoker) {
        super(CoreChannels.RestService);
        this.uriMatcher = uriMatcher;
        this.controllerInvoker = controllerInvoker;
        this.mapper = new ObjectMapper();
        parser = new JsonParser();
    }

    @PostConstruct
    public void setUp() {
        this.storeManager.createStore(CoreStores.ServiceWideHeaders);
		baseHostStore = this.storeManager.createStore(CoreStores.RestServiceHostConfig);
    
    }

    private String getBaseHost() {
        return baseHostStore.get(CoreStoreKeys.RestServiceBaseHost);
    }

    private String getBasePort() {
        return baseHostStore.get(CoreStoreKeys.RestServiceBasePort);
    }

    /**
     * Handle bus request.
     *
     * @param req RestServiceRequest instance sent on bus
     */
    @Override
    protected void handleServiceRequest(Request req, Message message) {

        RestOperation operation = new RestOperation();

        RestServiceRequest request = ClassMapper.CastPayload(RestServiceRequest.class, req);
        request.setHeaders((Map<String, String>) req.getHeaders());

        this.logDebugMessage(this.getClass().getSimpleName()
                + " handling Rest Request for URI: " + request.getUri().toASCIIString());

        // if application has over-ridden the base host, then we need to modify the URI.
        request.setUri(modifyURI(request.getUri()));

        operation.setUri(request.getUri());
        operation.setBody(request.getBody());
        operation.setMethod(request.getMethod());
        if (request.getHeaders() != null && request.getHeaders().keySet().size() > 0) {
            request.getHeaders().forEach((key, value) -> {
                operation.getHeaders().merge(key, value, (v, v2) -> v2);
            });
        }
        operation.setApiClass(request.getApiClass());
        operation.setId(req.getId());
        operation.setSentFrom(this.getName());

        // create a success handler to respond
        Consumer<Object> successHandler = (Object restResponseObject) -> {
            this.logDebugMessage(this.getClass().getSimpleName()
                    + " Successful REST response " + request.getUri().toASCIIString());

            // check if we got back a string / json, or an actual object.
            if (restResponseObject instanceof String) {
                JsonElement respJson = parser.parse(restResponseObject.toString());
                restResponseObject = respJson.toString();
            }

            Response response = new Response(req.getId(), restResponseObject);
            this.sendResponse(response, req.getId());
        };

        operation.setSuccessHandler(successHandler);

        // create an error handler to respond in case something goes wrong.
        Consumer<RestError> errorHandler = (RestError error) -> {
            this.logErrorMessage(this.getClass().getSimpleName()
                    + " Error with making REST response ", request.getUri().toASCIIString());

            Response response = new Response(req.getId(), error);
            response.setError(true);
            response.setErrorCode(error.errorCode);
            response.setErrorMessage(error.message);
            this.sendError(response, req.getId());
        };

        operation.setErrorHandler(errorHandler);

        this.restServiceRequest(operation);

    }


    private URI modifyURI(URI origUri) {
        if (getBaseHost() != null && getBaseHost().length() > 0) {
            String baseHost = getBaseHost();
            this.logDebugMessage(this.getClass().getSimpleName() + " using over-ridden base host: " + baseHost);

            String uri = origUri.toString();
            UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(uri);
            b.host(baseHost);

            // get the base port.
            String port = getBasePort();

            // if the port has also been changed
            if (port != null && port.length() > 0) {
                b.port(port);
            }

            // override the request URI, with the modified one, using over-ridden values
            return URI.create(b.toUriString());
        }
        return origUri;
    }

    /**
     * If calling the service via DI, then make the requested Rest Request locally via controller, or externally
     * via a standard rest call.
     *
     * @param operation RestOperation to be supplied
     */
    @Override
    protected void restServiceRequest(RestOperation operation) {

        // check if the URI is local to the system
        try {
            URIMethodResult methodResult = locateRestControllerForURIAndMethod(operation);
            if (methodResult != null && methodResult.getMethod() != null) {
                invokeRestController(methodResult, operation);
                return;
            }
        } catch (Exception e) {
            this.logErrorMessage("Exception when Locating & Invoking RestController ", e.toString());
            if (operation != null) {
                if(operation.getErrorHandler() != null) {
                    operation.getErrorHandler().accept(
                            new RestError("Exception when Locating & Invoking RestController", 500)
                    );
                }
            }
        }

        // if application has over-ridden the base host, then we need to modify the URI.
        operation.setUri(modifyURI(operation.getUri()));


        HttpEntity entity;
        HttpHeaders headers = new HttpHeaders();

        // fix patch issue.
        MediaType mediaType = new MediaType("application", "merge-patch+json");

        // check if headers are set.
        if (operation.getHeaders() != null) {
            Map<String, String> opHeaders = operation.getHeaders();
            Set<String> keySet = opHeaders.keySet();
            for (String key : keySet) {
                headers.add(key, opHeaders.get(key));
            }
        }
        if (headers.getContentType() == null) {
            headers.setContentType(mediaType);
        }
        entity = new HttpEntity<>(operation.getBody(), headers);

        // required because PATCH causes a freakout.
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        RestTemplate restTemplate = new RestTemplate(requestFactory);

        try {
            ResponseEntity resp;
            switch (operation.getMethod()) {
                case GET:
                    resp = restTemplate.exchange(
                            operation.getUri(),
                            HttpMethod.GET,
                            entity,
                            Class.forName(operation.getApiClass())
                    );
                    operation.getSuccessHandler().accept(resp.getBody());
                    break;

                case POST:
                    resp = restTemplate.exchange(
                            operation.getUri(),
                            HttpMethod.POST,
                            entity,
                            Class.forName(operation.getApiClass())
                    );
                    operation.getSuccessHandler().accept(resp.getBody());
                    break;

                case PUT:
                    resp = restTemplate.exchange(
                            operation.getUri(),
                            HttpMethod.PUT,
                            entity,
                            Class.forName(operation.getApiClass())
                    );
                    operation.getSuccessHandler().accept(resp.getBody());
                    break;

                case PATCH:
                    resp = restTemplate.exchange(
                            operation.getUri(),
                            HttpMethod.PATCH,
                            entity,
                            Class.forName(operation.getApiClass())
                    );
                    operation.getSuccessHandler().accept(resp.getBody());
                    break;

                case DELETE:
                    resp = restTemplate.exchange(
                            operation.getUri(),
                            HttpMethod.DELETE,
                            entity,
                            Class.forName(operation.getApiClass())
                    );
                    operation.getSuccessHandler().accept(resp.getBody());
                    break;
            }

        } catch (RestClientResponseException exp) {

            String errorMsg;
            int errorCode;

            try {
                // try parsing the error response as Transport Response and extract error code and message from it
                Response errorResponse = mapper.readValue(exp.getResponseBodyAsString(), Response.class);
                errorMsg = errorResponse.getErrorMessage();
                errorCode = errorResponse.getErrorCode();
            } catch (IOException ioe) {
                // if it's not a Transport response object, or some exception happened during casting, dump the entire
                // response as a String value.
                errorMsg = exp.getResponseBodyAsString();
                errorCode = exp.getRawStatusCode();
            }

            this.logErrorMessage("REST Client Error, unable to complete request: ", errorMsg);

            // try parsing the errorMsg as JSON and set it as errorObject
            Object upstreamErrorObject;
            ObjectMapper mapper = new ObjectMapper();
            try {
                upstreamErrorObject = mapper.readValue(errorMsg, LinkedHashMap.class);
            } catch (IOException e) {
                // errorMsg is not a properly formatted JSON string in which case just stick with the string value
                upstreamErrorObject = errorMsg;
            }

            RestError restError = new RestError("REST Client Error, unable to complete request: " + errorMsg,
                    upstreamErrorObject, errorCode);
            operation.getErrorHandler().accept(restError);

        } catch (NullPointerException npe) {

            this.logErrorMessage("Null Pointer Exception when making REST Call", npe.toString());
            operation.getErrorHandler().accept(
                    new RestError("Null Pointer exception thrown for: "
                            + operation.getUri().toString(), 500)
            );

        } catch (RuntimeException rex) {
            this.logErrorMessage("REST Client Error, unable to complete request: ", rex.toString());
            operation.getErrorHandler().accept(
                    new RestError("REST Client Error, unable to complete request: "
                            + operation.getUri().toString(), 500)
            );
        } catch (ClassNotFoundException cnfexp) {
            this.logErrorMessage("Class Not Found Exception when making REST Call", cnfexp.toString());
            operation.getErrorHandler().accept(
                    new RestError("Class Not Found Exception thrown for: "
                            + operation.getUri().toString(), 500)
            );
        }

    }

    private URIMethodResult locateRestControllerForURIAndMethod(RestOperation operation) throws Exception {

        URIMethodResult result = uriMatcher.findControllerMatch(
                operation.getUri(),
                RequestMethod.valueOf(operation.getMethod().toString())
        );

        if (result != null) {
            this.logDebugMessage("Located handling method for URI: "
                    + operation.getUri().getRawPath(), result.getMethod().getName());
        } else {
            this.logDebugMessage("Unable to locate a local handler for for URI: ",
                    operation.getUri().getRawPath());
        }
        return result;
    }

    private void invokeRestController(URIMethodResult result, RestOperation operation) {

        controllerInvoker.invokeMethod(result, operation);

    }
}
