/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomweb;

import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;

import com.mirth.connect.connectors.dicomweb.DICOMWebDispatcherProperties.AuthType;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorPropertiesInterface;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.purge.PurgeUtil;

public class WADORSDispatcherProperties extends ConnectorProperties
        implements DestinationConnectorPropertiesInterface {

    public enum RetrievalLevel { STUDY, SERIES, INSTANCE }

    private DestinationConnectorProperties destinationConnectorProperties;

    // Connection
    private String baseUrl;
    private AuthType authType;

    // Basic auth
    private String username;
    private String password;

    // Bearer
    private String bearerToken;

    // Google
    private String googleServiceAccountKeyFile;

    // Azure
    private String azureTenantId;
    private String azureClientId;
    private String azureClientSecret;
    private String azureScope;

    // AWS
    private String awsAccessKeyId;
    private String awsSecretAccessKey;
    private String awsRegion;
    private String awsService;

    // Retrieval
    private RetrievalLevel retrievalLevel;
    private String studyInstanceUid;
    private String seriesInstanceUid;
    private String sopInstanceUid;

    // Output
    private String outputFolder;
    private boolean overwriteExisting;

    // Timeouts
    private String connectTimeout;
    private String readTimeout;

    public WADORSDispatcherProperties() {
        destinationConnectorProperties = new DestinationConnectorProperties();

        baseUrl = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs";
        authType = AuthType.NONE;

        username = "";
        password = "";
        bearerToken = "";
        googleServiceAccountKeyFile = "";

        azureTenantId = "";
        azureClientId = "";
        azureClientSecret = "";
        azureScope = AzureAuthHelper.DEFAULT_SCOPE;

        awsAccessKeyId = "";
        awsSecretAccessKey = "";
        awsRegion = "us-east-1";
        awsService = "medical-imaging";

        retrievalLevel = RetrievalLevel.STUDY;
        studyInstanceUid = "";
        seriesInstanceUid = "";
        sopInstanceUid = "";

        outputFolder = "";
        overwriteExisting = false;

        connectTimeout = "30000";
        readTimeout = "120000";
    }

    public WADORSDispatcherProperties(WADORSDispatcherProperties props) {
        super(props);
        destinationConnectorProperties = new DestinationConnectorProperties(
                props.getDestinationConnectorProperties());

        baseUrl  = props.getBaseUrl();
        authType = props.getAuthType();

        username  = props.getUsername();
        password  = props.getPassword();
        bearerToken = props.getBearerToken();
        googleServiceAccountKeyFile = props.getGoogleServiceAccountKeyFile();

        azureTenantId     = props.getAzureTenantId();
        azureClientId     = props.getAzureClientId();
        azureClientSecret = props.getAzureClientSecret();
        azureScope        = props.getAzureScope();

        awsAccessKeyId     = props.getAwsAccessKeyId();
        awsSecretAccessKey = props.getAwsSecretAccessKey();
        awsRegion  = props.getAwsRegion();
        awsService = props.getAwsService();

        retrievalLevel    = props.getRetrievalLevel();
        studyInstanceUid  = props.getStudyInstanceUid();
        seriesInstanceUid = props.getSeriesInstanceUid();
        sopInstanceUid    = props.getSopInstanceUid();

        outputFolder      = props.getOutputFolder();
        overwriteExisting = props.isOverwriteExisting();

        connectTimeout = props.getConnectTimeout();
        readTimeout    = props.getReadTimeout();
    }

    // --- getters / setters ---

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public AuthType getAuthType() { return authType; }
    public void setAuthType(AuthType authType) { this.authType = authType; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }

    public String getGoogleServiceAccountKeyFile() { return googleServiceAccountKeyFile; }
    public void setGoogleServiceAccountKeyFile(String f) { this.googleServiceAccountKeyFile = f; }

    public String getAzureTenantId() { return azureTenantId; }
    public void setAzureTenantId(String v) { this.azureTenantId = v; }

    public String getAzureClientId() { return azureClientId; }
    public void setAzureClientId(String v) { this.azureClientId = v; }

    public String getAzureClientSecret() { return azureClientSecret; }
    public void setAzureClientSecret(String v) { this.azureClientSecret = v; }

    public String getAzureScope() { return azureScope; }
    public void setAzureScope(String v) { this.azureScope = v; }

    public String getAwsAccessKeyId() { return awsAccessKeyId; }
    public void setAwsAccessKeyId(String v) { this.awsAccessKeyId = v; }

    public String getAwsSecretAccessKey() { return awsSecretAccessKey; }
    public void setAwsSecretAccessKey(String v) { this.awsSecretAccessKey = v; }

    public String getAwsRegion() { return awsRegion; }
    public void setAwsRegion(String v) { this.awsRegion = v; }

    public String getAwsService() { return awsService; }
    public void setAwsService(String v) { this.awsService = v; }

    public RetrievalLevel getRetrievalLevel() { return retrievalLevel; }
    public void setRetrievalLevel(RetrievalLevel retrievalLevel) { this.retrievalLevel = retrievalLevel; }

    public String getStudyInstanceUid() { return studyInstanceUid; }
    public void setStudyInstanceUid(String v) { this.studyInstanceUid = v; }

    public String getSeriesInstanceUid() { return seriesInstanceUid; }
    public void setSeriesInstanceUid(String v) { this.seriesInstanceUid = v; }

    public String getSopInstanceUid() { return sopInstanceUid; }
    public void setSopInstanceUid(String v) { this.sopInstanceUid = v; }

    public String getOutputFolder() { return outputFolder; }
    public void setOutputFolder(String outputFolder) { this.outputFolder = outputFolder; }

    public boolean isOverwriteExisting() { return overwriteExisting; }
    public void setOverwriteExisting(boolean overwriteExisting) { this.overwriteExisting = overwriteExisting; }

    public String getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(String v) { this.connectTimeout = v; }

    public String getReadTimeout() { return readTimeout; }
    public void setReadTimeout(String v) { this.readTimeout = v; }

    @Override
    public DestinationConnectorProperties getDestinationConnectorProperties() {
        return destinationConnectorProperties;
    }

    @Override
    public String getProtocol() { return "DICOMWEBWADO"; }

    @Override
    public String getName() { return "DICOMweb WADO-RS Retrieve"; }

    @Override
    public String toFormattedString() {
        StringBuilder b = new StringBuilder();
        b.append("BASE URL: ").append(baseUrl).append("\n");
        b.append("LEVEL: ").append(retrievalLevel).append("\n");
        b.append("STUDY UID: ").append(studyInstanceUid).append("\n");
        if (retrievalLevel == RetrievalLevel.SERIES || retrievalLevel == RetrievalLevel.INSTANCE) {
            b.append("SERIES UID: ").append(seriesInstanceUid).append("\n");
        }
        if (retrievalLevel == RetrievalLevel.INSTANCE) {
            b.append("SOP INSTANCE UID: ").append(sopInstanceUid).append("\n");
        }
        b.append("OUTPUT FOLDER: ").append(outputFolder).append("\n");
        return b.toString();
    }

    @Override
    public ConnectorProperties clone() { return new WADORSDispatcherProperties(this); }

    @Override
    public boolean canValidateResponse() { return false; }

    @Override
    public boolean equals(Object obj) { return EqualsBuilder.reflectionEquals(this, obj); }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) { super.migrate3_1_0(element); }
    @Override public void migrate3_2_0(DonkeyElement element) {}
    @Override public void migrate3_3_0(DonkeyElement element) {}
    @Override public void migrate3_4_0(DonkeyElement element) {}
    @Override public void migrate3_5_0(DonkeyElement element) {}
    @Override public void migrate3_6_0(DonkeyElement element) {}
    @Override public void migrate3_7_0(DonkeyElement element) {}
    @Override public void migrate3_9_0(DonkeyElement element) {}
    @Override public void migrate3_11_0(DonkeyElement element) {}
    @Override public void migrate3_11_1(DonkeyElement element) {}
    @Override public void migrate3_12_0(DonkeyElement element) {} // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = super.getPurgedProperties();
        purgedProperties.put("destinationConnectorProperties",
                destinationConnectorProperties.getPurgedProperties());
        purgedProperties.put("authType", authType);
        purgedProperties.put("retrievalLevel", retrievalLevel);
        purgedProperties.put("overwriteExisting", overwriteExisting);
        purgedProperties.put("connectTimeout", PurgeUtil.getNumericValue(connectTimeout));
        purgedProperties.put("readTimeout", PurgeUtil.getNumericValue(readTimeout));
        return purgedProperties;
    }
}
