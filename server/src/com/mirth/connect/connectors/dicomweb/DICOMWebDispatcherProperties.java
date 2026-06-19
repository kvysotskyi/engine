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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorPropertiesInterface;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.purge.PurgeUtil;

public class DICOMWebDispatcherProperties extends ConnectorProperties implements DestinationConnectorPropertiesInterface {

    public enum AuthType {
        NONE, BASIC, BEARER, GOOGLE_SERVICE_ACCOUNT
    }

    private DestinationConnectorProperties destinationConnectorProperties;

    private String url;
    private AuthType authType;
    private String username;
    private String password;
    private String bearerToken;
    private String googleServiceAccountKeyFile;
    private String connectTimeout;
    private String readTimeout;
    private String template;

    public DICOMWebDispatcherProperties() {
        destinationConnectorProperties = new DestinationConnectorProperties();

        url = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies";
        authType = AuthType.NONE;
        username = "";
        password = "";
        bearerToken = "";
        googleServiceAccountKeyFile = "";
        connectTimeout = "30000";
        readTimeout = "60000";
        template = "${DICOMMESSAGE}";
    }

    public DICOMWebDispatcherProperties(DICOMWebDispatcherProperties props) {
        super(props);
        destinationConnectorProperties = new DestinationConnectorProperties(props.getDestinationConnectorProperties());

        url = props.getUrl();
        authType = props.getAuthType();
        username = props.getUsername();
        password = props.getPassword();
        bearerToken = props.getBearerToken();
        googleServiceAccountKeyFile = props.getGoogleServiceAccountKeyFile();
        connectTimeout = props.getConnectTimeout();
        readTimeout = props.getReadTimeout();
        template = props.getTemplate();
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

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

    public String getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(String connectTimeout) { this.connectTimeout = connectTimeout; }

    public String getReadTimeout() { return readTimeout; }
    public void setReadTimeout(String readTimeout) { this.readTimeout = readTimeout; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    @Override
    public DestinationConnectorProperties getDestinationConnectorProperties() {
        return destinationConnectorProperties;
    }

    @Override
    public String getProtocol() { return "DICOMWEB"; }

    @Override
    public String getName() { return "DICOMweb STOW-RS Sender"; }

    @Override
    public String toFormattedString() {
        StringBuilder b = new StringBuilder();
        b.append("URL: ").append(url).append("\n");
        b.append("AUTH: ").append(authType).append("\n");
        if (authType == AuthType.BASIC && StringUtils.isNotBlank(username)) {
            b.append("USERNAME: ").append(username).append("\n");
        }
        if (authType == AuthType.GOOGLE_SERVICE_ACCOUNT) {
            b.append("SERVICE ACCOUNT KEY: ").append(googleServiceAccountKeyFile).append("\n");
        }
        b.append("\n[CONTENT]\n").append(template);
        return b.toString();
    }

    @Override
    public ConnectorProperties clone() { return new DICOMWebDispatcherProperties(this); }

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
        purgedProperties.put("destinationConnectorProperties", destinationConnectorProperties.getPurgedProperties());
        purgedProperties.put("authType", authType);
        purgedProperties.put("connectTimeout", PurgeUtil.getNumericValue(connectTimeout));
        purgedProperties.put("readTimeout", PurgeUtil.getNumericValue(readTimeout));
        purgedProperties.put("templateLines", PurgeUtil.countLines(template));
        return purgedProperties;
    }
}
