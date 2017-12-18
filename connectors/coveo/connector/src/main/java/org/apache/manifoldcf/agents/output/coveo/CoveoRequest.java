/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed add
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance add
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * addOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.output.coveo;

import java.io.*;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

import com.google.gson.GsonBuilder;

import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;

import org.apache.manifoldcf.core.interfaces.ConfigParams;

public class CoveoRequest {
    private CoveoRequestType _requestType;
    private String _documentId;
    private HashMap<String, String> _metadata = new HashMap<>();

    /**
     * Constructor
     */
    public CoveoRequest() {
    }

    /**
     * Add a request type to the request (AddOrUpdate/Delete)
     *
     * @param requestType   is the type of request, taken from the request type enum
     * @return the actual class, to allow method chaining
     */
    public CoveoRequest addRequestType(CoveoRequestType requestType) {
        this._requestType = requestType;
        return this;
    }

    /**
     * Add a document id to the request (must be unique)
     *
     * @param documentId   is the id of the document to be processed, generally its URI
     * @return the actual class, to allow method chaining
     */
    public CoveoRequest addDocumentId(String documentId) {
        this._documentId = documentId;
        this._metadata.put("documentId", documentId);
        return this;
    }

    /**
     * Add the content of the document
     *
     * @param inputStream   is passed to the output connector, from the repository connector
     * @return the actual class, to allow method chaining
     */
    public CoveoRequest addDocumentBody(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder out = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            out.append(line);
        }

        this._metadata.put("binaryData", Base64.getEncoder().encodeToString(out.toString().getBytes(StandardCharsets.UTF_8)));

        return this;
    }

    /**
     * Add a single metadata to the document
     *
     * @param key   is the name of the metadata
     * @param value is the value of the metadata
     * @return the actual class, to allow method chaining
     */
    public CoveoRequest addMetadata(String key, String value) {
        this._metadata.put(key, value);
        return this;
    }

    /**
     * Add an authorized access control list to the document
     *
     * @param ACL   is the list of authorization
     * @return the actual class, to allow method chaining
     */
    public CoveoRequest addACL(Map ACL) {
        this._metadata.put("ACL", this.getJsonStringFromMap(ACL));
        return this;
    }

    /**
     * Add a denied access control list to the document
     *
     * @param denyACL   is the list of non-authorization
     * @return the actual class, to allow method chaining
     */
    public CoveoRequest addDenyACL(Map denyACL) {
        this._metadata.put("denyACL", this.getJsonStringFromMap(denyACL));
        return this;
    }

    /**
     * Convert a map object to a json string
     *
     * @param map   is the map to be converted to a json string
     * @return the json string
     */
    public String getJsonStringFromMap(Map map) {
        return new GsonBuilder().disableHtmlEscaping().create().toJson(map);
    }

    /**
     * Execute the request (will dispatch the request to the proper handler, depending on the request type)
     *
     * @param config   is the config of the output connector.
     * @return the http response from the Coveo push api call
     */
    public HttpResponse execute(ConfigParams config) throws Exception {
        String apiBaseUrl = config.getParameter(ParameterEnum.API_BASE_URL.name());
        String organizationId = config.getParameter(ParameterEnum.ORGANIZATION_ID.name());
        String sourceId = config.getParameter(ParameterEnum.SOURCE_ID.name());
        String apiKey = config.getParameter(ParameterEnum.API_KEY.name());

        if (this._requestType == CoveoRequestType.ADD_OR_UPDATE) {
            return this.executePUT(apiBaseUrl, organizationId, sourceId, apiKey);
        } else {
            return this.executeDELETE(apiBaseUrl, organizationId, sourceId, apiKey);
        }
    }

    /**
     * Execute the PUT request (add or update)
     *
     * @param apiBaseUrl   is from the ConfigParams of method "execute", it is the base url for the Coveo push API
     * @param organizationId   is from the ConfigParams of method "execute", it is the organization where to push the document
     * @param sourceId   is from the ConfigParams of method "execute", it is the source where to push the document
     * @param apiKey   is from the ConfigParams of method "execute", it is a valid api key to access Coveo push api
     * @return the http response from the Coveo push api call
     */
    private HttpResponse executePUT(String apiBaseUrl, String organizationId, String sourceId, String apiKey)
    throws Exception {
        HttpResponse response = null;

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPut request = new HttpPut(this.buildPutUrl(apiBaseUrl, organizationId, sourceId, this._documentId));

            StringEntity payload =new StringEntity(this.getJsonStringFromMap(this._metadata),"UTF-8");
            payload.setContentType("application/json");

            request.addHeader("content-type", "application/json");
            request.addHeader("Authorization", "Bearer " + apiKey);
            request.setEntity(payload);

            response = httpClient.execute(request);
        } catch (Exception e) {
            throw e;
        }

        return response;
    }

    /**
     * Build the proper PUT request URL, based on params
     *
     * @param apiBaseUrl   is from the ConfigParams of method "execute", it is the base url for the Coveo push API
     * @param organizationId   is from the ConfigParams of method "execute", it is the organization where to push the document
     * @param sourceId   is from the ConfigParams of method "execute", it is the source where to push the document
     * @return the built url, with the path parameters
     */
    private String buildPutUrl(String apiBaseUrl, String organizationId, String sourceId, String documentId) {
        String url = "%s/organizations/%s/sources/%s/documents?documentId=%s";

        // TODO: validate document id format

        url = String.format(url, apiBaseUrl, organizationId, sourceId, documentId);

        return url;
    }

    /**
     * Execute the DELETE request
     *
     * @param apiBaseUrl   is from the ConfigParams of method "execute", it is the base url for the Coveo push API
     * @param organizationId   is from the ConfigParams of method "execute", it is the organization where to push the document
     * @param sourceId   is from the ConfigParams of method "execute", it is the source where to push the document
     * @param apiKey   is from the ConfigParams of method "execute", it is a valid api key to access Coveo push api
     * @return the http response from the Coveo push api call
     */
    private HttpResponse executeDELETE(String apiBaseUrl, String organizationId, String sourceId, String apiKey) {
        HttpResponse response = null;

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpDelete request = new HttpDelete(this.buildDeleteUrl(apiBaseUrl, organizationId, sourceId, this._documentId));

            request.addHeader("content-type", "application/json");
            request.addHeader("Authorization", "Bearer " + apiKey);

            response = httpClient.execute(request);
        } catch (IOException e) {
            // TODO: handle the exception properly
        }

        return response;
    }

    /**
     * Build the proper DELETE request URL, based on params
     *
     * @param apiBaseUrl   is from the ConfigParams of method "execute", it is the base url for the Coveo push API
     * @param organizationId   is from the ConfigParams of method "execute", it is the organization where to push the document
     * @param sourceId   is from the ConfigParams of method "execute", it is the source where to push the document
     * @return the built url, with the path parameters
     */
    private String buildDeleteUrl(String apiBaseUrl, String organizationId, String sourceId, String documentId) {
        String url = "%s/organizations/%s/sources/%s/documents?deleteChildren=false&documentId=%s";

        url = String.format(url, apiBaseUrl, organizationId, sourceId, documentId);

        return url;
    }
}