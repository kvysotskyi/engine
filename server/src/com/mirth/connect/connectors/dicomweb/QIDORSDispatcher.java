/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomweb;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.dicomweb.AzureAuthHelper.Token;
import com.mirth.connect.connectors.dicomweb.DICOMWebDispatcherProperties.AuthType;
import com.mirth.connect.connectors.dicomweb.QIDORSDispatcherProperties.QueryLevel;
import com.mirth.connect.connectors.dicomweb.QIDORSDispatcherProperties.ResponseFormat;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.model.event.ErrorEventType;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.donkey.server.event.ErrorEvent;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.util.TemplateValueReplacer;
import com.mirth.connect.util.ErrorMessageBuilder;

public class QIDORSDispatcher extends DestinationConnector {

    private Logger logger = LogManager.getLogger(this.getClass());
    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private TemplateValueReplacer replacer = new TemplateValueReplacer();

    private QIDORSDispatcherProperties connectorProperties;
    private CloseableHttpClient httpClient;

    private volatile GoogleAuthHelper.Token googleToken;
    private volatile Token azureToken;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        connectorProperties = (QIDORSDispatcherProperties) getConnectorProperties();
    }

    @Override
    public void onStart() throws ConnectorTaskException {
        int connectMs = NumberUtils.toInt(connectorProperties.getConnectTimeout(), 30000);
        int readMs    = NumberUtils.toInt(connectorProperties.getReadTimeout(), 60000);

        RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(connectMs)
                .setSocketTimeout(readMs)
                .setConnectionRequestTimeout(connectMs)
                .build();

        httpClient = HttpClients.custom().setDefaultRequestConfig(cfg).build();
    }

    @Override
    public void onStop() throws ConnectorTaskException { closeHttpClient(); }

    @Override
    public void onHalt() throws ConnectorTaskException { closeHttpClient(); }

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    private void closeHttpClient() {
        CloseableHttpClient c = httpClient;
        httpClient = null;
        if (c != null) {
            try { c.close(); } catch (IOException e) {
                logger.warn("Error closing HTTP client", e);
            }
        }
    }

    @Override
    public void replaceConnectorProperties(ConnectorProperties connectorProperties,
            ConnectorMessage connectorMessage) {
        QIDORSDispatcherProperties p = (QIDORSDispatcherProperties) connectorProperties;
        p.setBaseUrl(replacer.replaceValues(p.getBaseUrl(), connectorMessage));
        p.setStudyInstanceUid(replacer.replaceValues(p.getStudyInstanceUid(), connectorMessage));
        p.setSeriesInstanceUid(replacer.replaceValues(p.getSeriesInstanceUid(), connectorMessage));
        p.setQueryParams(replacer.replaceValues(p.getQueryParams(), connectorMessage));
        p.setLimit(replacer.replaceValues(p.getLimit(), connectorMessage));
        p.setOffset(replacer.replaceValues(p.getOffset(), connectorMessage));
        p.setIncludeFields(replacer.replaceValues(p.getIncludeFields(), connectorMessage));
        p.setUsername(replacer.replaceValues(p.getUsername(), connectorMessage));
        p.setPassword(replacer.replaceValues(p.getPassword(), connectorMessage));
        p.setBearerToken(replacer.replaceValues(p.getBearerToken(), connectorMessage));
    }

    @Override
    public Response send(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        QIDORSDispatcherProperties props = (QIDORSDispatcherProperties) connectorProperties;

        String responseData = null;
        String responseError = null;
        String responseStatusMessage = null;
        Status responseStatus = Status.QUEUED;

        String url = null;
        try {
            url = buildUrl(props);
        } catch (Exception e) {
            return new Response(Status.ERROR, null,
                    "Failed to build QIDO-RS URL: " + e.getMessage(),
                    ErrorMessageBuilder.buildErrorMessage(props.getName(), e.getMessage(), null));
        }

        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(),
                getDestinationName(), ConnectionStatusEventType.READING, "QIDO-RS: " + url));

        try {
            HttpGet get = new HttpGet(url);
            String accept = props.getResponseFormat() == ResponseFormat.JSON
                    ? "application/dicom+json" : "application/dicom+xml";
            get.setHeader("Accept", accept);

            applyAuth(get, props);

            try (CloseableHttpResponse httpResponse = httpClient.execute(get)) {
                int code = httpResponse.getStatusLine().getStatusCode();
                HttpEntity entity = httpResponse.getEntity();
                responseData = entity != null
                        ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";

                if (code == 200) {
                    responseStatus = Status.SENT;
                    responseStatusMessage = "QIDO-RS: HTTP 200";
                } else if (code == 204) {
                    // No matching results — return an empty JSON array or empty body
                    responseData = props.getResponseFormat() == ResponseFormat.JSON ? "[]" : "";
                    responseStatus = Status.SENT;
                    responseStatusMessage = "QIDO-RS: HTTP 204 (no results)";
                } else if (code >= 500) {
                    responseStatus = Status.QUEUED;
                    responseStatusMessage = "QIDO-RS server error: HTTP " + code;
                } else {
                    responseStatus = Status.ERROR;
                    responseStatusMessage = "QIDO-RS rejected: HTTP " + code;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseStatus = Status.QUEUED;
            responseStatusMessage = "Send interrupted";
        } catch (Exception e) {
            responseStatusMessage = ErrorMessageBuilder.buildErrorResponse(e.getMessage(), e);
            responseError = ErrorMessageBuilder.buildErrorMessage(
                    connectorProperties.getName(), e.getMessage(), null);
            eventController.dispatchEvent(new ErrorEvent(getChannelId(), getMetaDataId(),
                    connectorMessage.getMessageId(), ErrorEventType.DESTINATION_CONNECTOR,
                    getDestinationName(), connectorProperties.getName(), e.getMessage(), null));
        } finally {
            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(),
                    getDestinationName(), ConnectionStatusEventType.IDLE));
        }

        return new Response(responseStatus, responseData, responseStatusMessage, responseError);
    }

    // -------------------------------------------------------------------------
    // URL builder
    // -------------------------------------------------------------------------

    private String buildUrl(QIDORSDispatcherProperties props) throws Exception {
        String base = props.getBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        StringBuilder url = new StringBuilder(base);

        switch (props.getQueryLevel()) {
            case STUDIES:
                url.append("/studies");
                break;
            case SERIES:
                url.append("/studies/")
                   .append(urlEncodePath(props.getStudyInstanceUid()))
                   .append("/series");
                break;
            case INSTANCES:
                url.append("/studies/")
                   .append(urlEncodePath(props.getStudyInstanceUid()))
                   .append("/series/")
                   .append(urlEncodePath(props.getSeriesInstanceUid()))
                   .append("/instances");
                break;
        }

        List<String> params = new ArrayList<>();

        // User-specified query params: one "Keyword=Value" per line
        if (StringUtils.isNotBlank(props.getQueryParams())) {
            for (String line : props.getQueryParams().split("[\r\n]+")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String key   = URLEncoder.encode(line.substring(0, eq).trim(), "UTF-8");
                String value = URLEncoder.encode(line.substring(eq + 1).trim(), "UTF-8");
                params.add(key + "=" + value);
            }
        }

        if (StringUtils.isNotBlank(props.getLimit())) {
            params.add("limit=" + URLEncoder.encode(props.getLimit().trim(), "UTF-8"));
        }
        if (StringUtils.isNotBlank(props.getOffset())) {
            params.add("offset=" + URLEncoder.encode(props.getOffset().trim(), "UTF-8"));
        }
        if (props.isFuzzyMatching()) {
            params.add("fuzzymatching=true");
        }
        if (StringUtils.isNotBlank(props.getIncludeFields())) {
            for (String field : props.getIncludeFields().split("[,\\s]+")) {
                field = field.trim();
                if (!field.isEmpty()) {
                    params.add("includefield=" + URLEncoder.encode(field, "UTF-8"));
                }
            }
        }

        if (!params.isEmpty()) {
            url.append("?").append(String.join("&", params));
        }

        return url.toString();
    }

    private String urlEncodePath(String uid) throws Exception {
        // UIDs contain only digits and dots — safe, but encode for correctness
        return URLEncoder.encode(uid.trim(), "UTF-8");
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    private void applyAuth(HttpGet get, QIDORSDispatcherProperties props) throws Exception {
        switch (props.getAuthType()) {
            case BASIC:
                if (StringUtils.isNotBlank(props.getUsername())) {
                    String creds = props.getUsername() + ":" + props.getPassword();
                    get.setHeader("Authorization", "Basic " +
                            Base64.getEncoder().encodeToString(
                                    creds.getBytes(StandardCharsets.UTF_8)));
                }
                break;

            case BEARER:
                if (StringUtils.isNotBlank(props.getBearerToken())) {
                    get.setHeader("Authorization", "Bearer " + props.getBearerToken());
                }
                break;

            case GOOGLE_SERVICE_ACCOUNT:
                get.setHeader("Authorization", "Bearer " + getGoogleToken(props));
                break;

            case AZURE_SERVICE_PRINCIPAL:
                get.setHeader("Authorization", "Bearer " + getAzureToken(props));
                break;

            case AWS_SIG_V4:
                AwsSigV4Helper.sign(get, new byte[0],
                        props.getAwsRegion(), props.getAwsService(),
                        props.getAwsAccessKeyId(), props.getAwsSecretAccessKey());
                break;

            case NONE:
            default:
                break;
        }
    }

    private synchronized String getGoogleToken(QIDORSDispatcherProperties props) throws Exception {
        if (googleToken == null || googleToken.isExpired()) {
            googleToken = GoogleAuthHelper.fetchToken(props.getGoogleServiceAccountKeyFile());
        }
        return googleToken.value;
    }

    private synchronized String getAzureToken(QIDORSDispatcherProperties props) throws Exception {
        if (azureToken == null || azureToken.isExpired()) {
            azureToken = AzureAuthHelper.fetchToken(
                    props.getAzureTenantId(), props.getAzureClientId(),
                    props.getAzureClientSecret(), props.getAzureScope());
        }
        return azureToken.value;
    }
}
