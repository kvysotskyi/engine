/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomweb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.mirth.connect.connectors.dicomweb.WADORSDispatcherProperties.RetrievalLevel;
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

public class WADORSDispatcher extends DestinationConnector {

    private Logger logger = LogManager.getLogger(this.getClass());
    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private TemplateValueReplacer replacer = new TemplateValueReplacer();

    private WADORSDispatcherProperties connectorProperties;
    private CloseableHttpClient httpClient;

    private volatile GoogleAuthHelper.Token googleToken;
    private volatile Token azureToken;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        connectorProperties = (WADORSDispatcherProperties) getConnectorProperties();
    }

    @Override
    public void onStart() throws ConnectorTaskException {
        int connectMs = NumberUtils.toInt(connectorProperties.getConnectTimeout(), 30000);
        int readMs    = NumberUtils.toInt(connectorProperties.getReadTimeout(), 120000);

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
        WADORSDispatcherProperties p = (WADORSDispatcherProperties) connectorProperties;
        p.setBaseUrl(replacer.replaceValues(p.getBaseUrl(), connectorMessage));
        p.setStudyInstanceUid(replacer.replaceValues(p.getStudyInstanceUid(), connectorMessage));
        p.setSeriesInstanceUid(replacer.replaceValues(p.getSeriesInstanceUid(), connectorMessage));
        p.setSopInstanceUid(replacer.replaceValues(p.getSopInstanceUid(), connectorMessage));
        p.setOutputFolder(replacer.replaceValues(p.getOutputFolder(), connectorMessage));
        p.setUsername(replacer.replaceValues(p.getUsername(), connectorMessage));
        p.setPassword(replacer.replaceValues(p.getPassword(), connectorMessage));
        p.setBearerToken(replacer.replaceValues(p.getBearerToken(), connectorMessage));
    }

    @Override
    public Response send(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        WADORSDispatcherProperties props = (WADORSDispatcherProperties) connectorProperties;

        String responseData = null;
        String responseError = null;
        String responseStatusMessage = null;
        Status responseStatus = Status.QUEUED;

        if (StringUtils.isBlank(props.getOutputFolder())) {
            return new Response(Status.ERROR, null,
                    "Output folder is not configured.",
                    ErrorMessageBuilder.buildErrorMessage(props.getName(),
                            "Output folder is not configured.", null));
        }

        String url;
        try {
            url = buildUrl(props);
        } catch (Exception e) {
            return new Response(Status.ERROR, null,
                    "Failed to build WADO-RS URL: " + e.getMessage(),
                    ErrorMessageBuilder.buildErrorMessage(props.getName(), e.getMessage(), null));
        }

        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(),
                getDestinationName(), ConnectionStatusEventType.READING, "WADO-RS: " + url));

        try {
            HttpGet get = new HttpGet(url);
            get.setHeader("Accept", "multipart/related; type=\"application/dicom\"");

            applyAuth(get, props);

            try (CloseableHttpResponse httpResponse = httpClient.execute(get)) {
                int code = httpResponse.getStatusLine().getStatusCode();
                HttpEntity entity = httpResponse.getEntity();

                if (code == 200 && entity != null) {
                    String contentType = entity.getContentType() != null
                            ? entity.getContentType().getValue() : "";
                    byte[] body = EntityUtils.toByteArray(entity);

                    List<String> savedPaths;
                    if (contentType.toLowerCase().contains("multipart/related")) {
                        String boundary = extractBoundary(contentType);
                        if (boundary == null) {
                            throw new IOException("No boundary in Content-Type: " + contentType);
                        }
                        List<byte[]> parts = parseMultipartParts(body, boundary);
                        savedPaths = saveParts(parts, props.getOutputFolder(),
                                props.isOverwriteExisting());
                    } else {
                        // Single-part response (some servers return application/dicom directly)
                        String path = saveDicomFile(body, props.getOutputFolder(), 0,
                                props.isOverwriteExisting());
                        savedPaths = new ArrayList<>();
                        savedPaths.add(path);
                    }

                    responseData = toJsonArray(savedPaths);
                    responseStatus = Status.SENT;
                    responseStatusMessage = "WADO-RS: " + savedPaths.size() + " instance(s) saved";

                } else if (code == 200) {
                    responseData = "[]";
                    responseStatus = Status.SENT;
                    responseStatusMessage = "WADO-RS: HTTP 200 (empty body)";
                } else if (code >= 500) {
                    responseStatus = Status.QUEUED;
                    responseStatusMessage = "WADO-RS server error: HTTP " + code;
                } else {
                    String body = entity != null
                            ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";
                    responseStatus = Status.ERROR;
                    responseStatusMessage = "WADO-RS rejected: HTTP " + code;
                    responseData = body;
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

    private String buildUrl(WADORSDispatcherProperties props) throws Exception {
        String base = props.getBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        StringBuilder url = new StringBuilder(base);
        url.append("/studies/").append(urlEncodePath(props.getStudyInstanceUid()));

        switch (props.getRetrievalLevel()) {
            case SERIES:
                url.append("/series/").append(urlEncodePath(props.getSeriesInstanceUid()));
                break;
            case INSTANCE:
                url.append("/series/").append(urlEncodePath(props.getSeriesInstanceUid()))
                   .append("/instances/").append(urlEncodePath(props.getSopInstanceUid()));
                break;
            case STUDY:
            default:
                break;
        }

        return url.toString();
    }

    private String urlEncodePath(String uid) throws Exception {
        return URLEncoder.encode(uid.trim(), "UTF-8");
    }

    // -------------------------------------------------------------------------
    // Multipart parsing
    // -------------------------------------------------------------------------

    private List<byte[]> parseMultipartParts(byte[] body, String boundary) {
        List<byte[]> parts = new ArrayList<>();

        byte[] firstDelim  = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] innerDelim  = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] headerEnd   = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] crlf        = "\r\n".getBytes(StandardCharsets.ISO_8859_1);

        // Find the first boundary
        int pos = findBytes(body, firstDelim, 0);
        if (pos < 0) return parts;

        // Skip past the first boundary line (boundary + CRLF)
        pos += firstDelim.length;
        if (pos + 2 <= body.length && body[pos] == '\r' && body[pos + 1] == '\n') {
            pos += 2;
        }

        while (pos < body.length) {
            // Check for closing boundary (--)
            if (pos + 2 <= body.length && body[pos] == '-' && body[pos + 1] == '-') {
                break;
            }

            // Find end of part headers
            int dataStart = findBytes(body, headerEnd, pos);
            if (dataStart < 0) break;
            dataStart += headerEnd.length;

            // Find next boundary (preceded by CRLF)
            int dataEnd = findBytes(body, innerDelim, dataStart);
            if (dataEnd < 0) {
                // Last part — take remaining bytes
                parts.add(Arrays.copyOfRange(body, dataStart, body.length));
                break;
            }

            parts.add(Arrays.copyOfRange(body, dataStart, dataEnd));

            // Advance past inner delimiter + CRLF
            pos = dataEnd + innerDelim.length;
            if (pos + 2 <= body.length && body[pos] == '\r' && body[pos + 1] == '\n') {
                pos += 2;
            }
        }

        return parts;
    }

    private static int findBytes(byte[] haystack, byte[] needle, int start) {
        outer:
        for (int i = start; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.toLowerCase().startsWith("boundary=")) {
                String boundary = part.substring("boundary=".length()).trim();
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // File saving
    // -------------------------------------------------------------------------

    private List<String> saveParts(List<byte[]> parts, String folder,
            boolean overwrite) throws IOException {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            paths.add(saveDicomFile(parts.get(i), folder, i, overwrite));
        }
        return paths;
    }

    private String saveDicomFile(byte[] dicomBytes, String folder, int index,
            boolean overwrite) throws IOException {
        File dir = new File(folder);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create output folder: " + folder);
        }

        String uid = readSopInstanceUid(dicomBytes);
        String filename = (uid != null && !uid.isEmpty())
                ? uid + ".dcm"
                : String.format("instance_%04d.dcm", index);

        File file = new File(dir, filename);
        if (!overwrite && file.exists()) {
            logger.debug("Skipping existing file: {}", file.getAbsolutePath());
            return file.getAbsolutePath();
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(dicomBytes);
        }
        return file.getAbsolutePath();
    }

    /**
     * Reads the SOPInstanceUID (0008,0018) from a DICOM Part 10 byte array without
     * requiring the dcm4che library on the classpath, using a minimal explicit-VR parser.
     */
    private String readSopInstanceUid(byte[] dicomBytes) {
        // Part 10 file: 128-byte preamble + "DICM" magic at offset 128
        if (dicomBytes.length < 132) return null;
        if (dicomBytes[128] != 'D' || dicomBytes[129] != 'I'
                || dicomBytes[130] != 'C' || dicomBytes[131] != 'M') {
            return null;
        }

        int offset = 132;
        while (offset + 8 <= dicomBytes.length) {
            int group   = u16le(dicomBytes, offset);
            int element = u16le(dicomBytes, offset + 2);

            // All tags in group 0002 (File Meta) and 0008 (Identifying) use explicit VR
            String vr = new String(dicomBytes, offset + 4, 2, StandardCharsets.ISO_8859_1);

            int valueLength;
            int valueOffset;

            // Explicit VR: long-form if VR is OB OD OF OL OW SQ UC UN UR UT
            if (isLongVr(vr)) {
                if (offset + 12 > dicomBytes.length) break;
                valueLength = u32le(dicomBytes, offset + 8);
                valueOffset = offset + 12;
            } else {
                valueLength = u16le(dicomBytes, offset + 6);
                valueOffset = offset + 8;
            }

            if (group == 0x0008 && element == 0x0018) {
                // SOPInstanceUID
                if (valueLength > 0 && valueOffset + valueLength <= dicomBytes.length) {
                    return new String(dicomBytes, valueOffset, valueLength,
                            StandardCharsets.ISO_8859_1).trim();
                }
                return null;
            }

            // Stop searching after group 0008 — SOP UID should always be in 0008
            if (group > 0x0008) break;

            if (valueLength < 0 || valueOffset + valueLength > dicomBytes.length) break;
            offset = valueOffset + valueLength;
        }
        return null;
    }

    private static boolean isLongVr(String vr) {
        switch (vr) {
            case "OB": case "OD": case "OF": case "OL": case "OW":
            case "SQ": case "UC": case "UN": case "UR": case "UT":
                return true;
            default:
                return false;
        }
    }

    private static int u16le(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int u32le(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
             | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    // -------------------------------------------------------------------------
    // JSON helper (avoids external library dependency)
    // -------------------------------------------------------------------------

    private static String toJsonArray(List<String> paths) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append('"');
            for (char c : paths.get(i).toCharArray()) {
                if (c == '\\') sb.append("\\\\");
                else if (c == '"') sb.append("\\\"");
                else sb.append(c);
            }
            sb.append('"');
        }
        sb.append("]");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    private void applyAuth(HttpGet get, WADORSDispatcherProperties props) throws Exception {
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

    private synchronized String getGoogleToken(WADORSDispatcherProperties props) throws Exception {
        if (googleToken == null || googleToken.isExpired()) {
            googleToken = GoogleAuthHelper.fetchToken(props.getGoogleServiceAccountKeyFile());
        }
        return googleToken.value;
    }

    private synchronized String getAzureToken(WADORSDispatcherProperties props) throws Exception {
        if (azureToken == null || azureToken.isExpired()) {
            azureToken = AzureAuthHelper.fetchToken(
                    props.getAzureTenantId(), props.getAzureClientId(),
                    props.getAzureClientSecret(), props.getAzureScope());
        }
        return azureToken.value;
    }
}
