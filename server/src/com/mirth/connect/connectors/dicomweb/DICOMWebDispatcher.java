/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomweb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.dicomweb.AzureAuthHelper.Token;
import com.mirth.connect.connectors.dicomweb.DICOMWebDispatcherProperties.AuthType;
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

public class DICOMWebDispatcher extends DestinationConnector {

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private Logger logger = LogManager.getLogger(this.getClass());
    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private TemplateValueReplacer replacer = new TemplateValueReplacer();

    private DICOMWebDispatcherProperties connectorProperties;
    private CloseableHttpClient httpClient;

    /** Cached tokens — refreshed lazily, guarded by their own monitors. */
    private volatile GoogleAuthHelper.Token googleToken;
    private volatile Token azureToken;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        connectorProperties = (DICOMWebDispatcherProperties) getConnectorProperties();
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
    public void replaceConnectorProperties(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        DICOMWebDispatcherProperties p = (DICOMWebDispatcherProperties) connectorProperties;
        p.setUrl(replacer.replaceValues(p.getUrl(), connectorMessage));
        p.setUsername(replacer.replaceValues(p.getUsername(), connectorMessage));
        p.setPassword(replacer.replaceValues(p.getPassword(), connectorMessage));
        p.setBearerToken(replacer.replaceValues(p.getBearerToken(), connectorMessage));
        p.setTemplate(replacer.replaceValues(p.getTemplate(), connectorMessage));
    }

    @Override
    public Response send(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        DICOMWebDispatcherProperties props = (DICOMWebDispatcherProperties) connectorProperties;

        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(),
                getDestinationName(), ConnectionStatusEventType.WRITING, "URL: " + props.getUrl()));

        String responseData = null;
        String responseError = null;
        String responseStatusMessage = null;
        Status responseStatus = Status.QUEUED;

        try {
            byte[] dicomBytes = getDicomBytes(props, connectorMessage);
            String boundary = "stowrs-" + UUID.randomUUID().toString();
            byte[] body = buildMultipartBody(dicomBytes, boundary);

            HttpPost post = new HttpPost(props.getUrl());
            post.setHeader("Content-Type",
                    "multipart/related; type=\"application/dicom\"; boundary=" + boundary);
            post.setHeader("Accept", "application/dicom+xml");
            post.setEntity(new ByteArrayEntity(body));

            // Auth must be applied after all headers/entity are set (AWS SigV4 signs them)
            applyAuth(post, props, body);

            try (CloseableHttpResponse httpResponse = httpClient.execute(post)) {
                int code = httpResponse.getStatusLine().getStatusCode();
                HttpEntity entity = httpResponse.getEntity();
                responseData = entity != null
                        ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";

                if (code == 200 || code == 202) {
                    responseStatus = Status.SENT;
                    responseStatusMessage = "STOW-RS accepted: HTTP " + code;
                } else if (code >= 500) {
                    responseStatus = Status.QUEUED;
                    responseStatusMessage = "STOW-RS server error: HTTP " + code;
                } else {
                    responseStatus = Status.ERROR;
                    responseStatusMessage = "STOW-RS rejected: HTTP " + code;
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
    // Auth
    // -------------------------------------------------------------------------

    private void applyAuth(HttpPost post, DICOMWebDispatcherProperties props, byte[] body)
            throws Exception {
        switch (props.getAuthType()) {
            case BASIC:
                if (StringUtils.isNotBlank(props.getUsername())) {
                    String creds = props.getUsername() + ":" + props.getPassword();
                    post.setHeader("Authorization", "Basic " +
                            Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
                }
                break;

            case BEARER:
                if (StringUtils.isNotBlank(props.getBearerToken())) {
                    post.setHeader("Authorization", "Bearer " + props.getBearerToken());
                }
                break;

            case GOOGLE_SERVICE_ACCOUNT:
                post.setHeader("Authorization", "Bearer " + getGoogleToken(props));
                break;

            case AZURE_SERVICE_PRINCIPAL:
                post.setHeader("Authorization", "Bearer " + getAzureToken(props));
                break;

            case AWS_SIG_V4:
                AwsSigV4Helper.sign(post, body,
                        props.getAwsRegion(), props.getAwsService(),
                        props.getAwsAccessKeyId(), props.getAwsSecretAccessKey());
                break;

            case NONE:
            default:
                break;
        }
    }

    private synchronized String getGoogleToken(DICOMWebDispatcherProperties props) throws Exception {
        if (googleToken == null || googleToken.isExpired()) {
            logger.debug("Fetching Google Cloud Healthcare access token");
            googleToken = GoogleAuthHelper.fetchToken(props.getGoogleServiceAccountKeyFile());
        }
        return googleToken.value;
    }

    private synchronized String getAzureToken(DICOMWebDispatcherProperties props) throws Exception {
        if (azureToken == null || azureToken.isExpired()) {
            logger.debug("Fetching Azure AD access token");
            azureToken = AzureAuthHelper.fetchToken(
                    props.getAzureTenantId(), props.getAzureClientId(),
                    props.getAzureClientSecret(), props.getAzureScope());
        }
        return azureToken.value;
    }

    // -------------------------------------------------------------------------
    // DICOM bytes + multipart body
    // -------------------------------------------------------------------------

    private byte[] getDicomBytes(DICOMWebDispatcherProperties props, ConnectorMessage connectorMessage)
            throws Exception {
        Map<String, Object> sourceMap = connectorMessage.getSourceMap();
        if (sourceMap != null) {
            Object dicomFilePath = sourceMap.get("dicomFile");
            if (dicomFilePath instanceof String) {
                File file = new File((String) dicomFilePath);
                if (file.exists()) {
                    try (FileInputStream fis = new FileInputStream(file)) {
                        return IOUtils.toByteArray(fis);
                    }
                }
            }
        }
        return getAttachmentHandlerProvider().reAttachMessage(
                props.getTemplate(), connectorMessage, null, true,
                props.getDestinationConnectorProperties().isReattachAttachments());
    }

    private byte[] buildMultipartBody(byte[] dicomBytes, String boundary) throws IOException {
        byte[] boundaryLine   = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] contentTypeLine = "Content-Type: application/dicom".getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream out = new ByteArrayOutputStream(dicomBytes.length + 256);
        out.write(boundaryLine);   out.write(CRLF);
        out.write(contentTypeLine); out.write(CRLF);
        out.write(CRLF);
        out.write(dicomBytes);     out.write(CRLF);
        out.write(boundaryLine);   out.write("--".getBytes(StandardCharsets.US_ASCII));
        out.write(CRLF);
        return out.toByteArray();
    }
}
