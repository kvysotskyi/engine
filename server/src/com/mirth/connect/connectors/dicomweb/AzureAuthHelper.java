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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * Fetches Azure AD access tokens via the OAuth2 client credentials grant.
 * Used for Azure Health Data Services DICOM service and similar Azure-hosted DICOM endpoints.
 *
 * Token endpoint: https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token
 * Default scope:  https://dicom.healthcareapis.azure.com/.default
 */
class AzureAuthHelper {

    static final String DEFAULT_SCOPE = "https://dicom.healthcareapis.azure.com/.default";

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN   = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");

    static final class Token {
        final String value;
        final long expiresAtEpochSec;

        Token(String value, long expiresIn) {
            this.value = value;
            this.expiresAtEpochSec = System.currentTimeMillis() / 1000L + expiresIn;
        }

        boolean isExpired() {
            return System.currentTimeMillis() / 1000L >= expiresAtEpochSec - 60;
        }
    }

    static Token fetchToken(String tenantId, String clientId, String clientSecret, String scope)
            throws Exception {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(tokenUrl);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));
            params.add(new BasicNameValuePair("client_id", clientId));
            params.add(new BasicNameValuePair("client_secret", clientSecret));
            params.add(new BasicNameValuePair("scope", scope));
            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (status != 200) {
                    throw new IOException("Azure token request failed (HTTP " + status + "): " + body);
                }

                Matcher tokenMatcher = ACCESS_TOKEN.matcher(body);
                if (!tokenMatcher.find()) {
                    throw new IOException("No access_token in Azure response: " + body);
                }
                long expiresIn = 3600;
                Matcher expMatcher = EXPIRES_IN.matcher(body);
                if (expMatcher.find()) {
                    expiresIn = Long.parseLong(expMatcher.group(1));
                }
                return new Token(tokenMatcher.group(1), expiresIn);
            }
        }
    }
}
