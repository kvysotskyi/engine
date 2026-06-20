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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Fetches Google OAuth2 access tokens from a service account JSON key file.
 * Implements the JWT Bearer grant (RFC 7523) with SHA256withRSA signing using
 * only JDK built-ins — no external Google libraries required.
 */
class GoogleAuthHelper {

    static final String CLOUD_HEALTHCARE_SCOPE = "https://www.googleapis.com/auth/cloud-healthcare";
    private static final String JWT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private static final Pattern SA_FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");

    static class Token {
        final String value;
        final long expiresAtEpochSec;

        Token(String value, long expiresIn) {
            this.value = value;
            this.expiresAtEpochSec = System.currentTimeMillis() / 1000L + expiresIn;
        }

        boolean isExpired() {
            // Refresh 60 s early to avoid clock-skew issues
            return System.currentTimeMillis() / 1000L >= expiresAtEpochSec - 60;
        }
    }

    /** Reads the service account key file and fetches a Cloud Healthcare access token. */
    static Token fetchToken(String keyFilePath) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get(keyFilePath)), StandardCharsets.UTF_8);
        Map<String, String> fields = parseFields(json);

        String email    = require(fields, "client_email");
        String keyId    = fields.get("private_key_id");   // optional
        String pemKey   = require(fields, "private_key");
        String tokenUri = fields.getOrDefault("token_uri", "https://oauth2.googleapis.com/token");

        String jwt = buildJwt(email, keyId, pemKey, tokenUri);
        return exchangeForToken(jwt, tokenUri);
    }

    // -------------------------------------------------------------------------

    private static String buildJwt(String email, String keyId, String pemKey, String tokenUri)
            throws Exception {
        String headerJson = keyId != null
                ? "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + keyId + "\"}"
                : "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";

        long now = System.currentTimeMillis() / 1000L;
        String payloadJson = "{\"iss\":\"" + email + "\""
                + ",\"scope\":\"" + CLOUD_HEALTHCARE_SCOPE + "\""
                + ",\"aud\":\"" + tokenUri + "\""
                + ",\"iat\":" + now
                + ",\"exp\":" + (now + 3600) + "}";

        String header  = b64url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = b64url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String toSign  = header + "." + payload;

        PrivateKey key = loadPrivateKey(pemKey);
        Signature sig  = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(toSign.getBytes(StandardCharsets.UTF_8));

        return toSign + "." + b64url(sig.sign());
    }

    private static Token exchangeForToken(String jwt, String tokenUri) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(tokenUri);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", JWT_GRANT_TYPE));
            params.add(new BasicNameValuePair("assertion", jwt));
            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (status != 200) {
                    throw new IOException("Token exchange failed (HTTP " + status + "): " + body);
                }

                Matcher tokenMatcher = ACCESS_TOKEN.matcher(body);
                if (!tokenMatcher.find()) {
                    throw new IOException("No access_token in response: " + body);
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

    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static Map<String, String> parseFields(String json) {
        Map<String, String> fields = new HashMap<>();
        Matcher m = SA_FIELD.matcher(json);
        while (m.find()) {
            fields.put(m.group(1), m.group(2).replace("\\n", "\n").replace("\\\\", "\\"));
        }
        return fields;
    }

    private static String require(Map<String, String> map, String key) throws IOException {
        String v = map.get(key);
        if (v == null || v.isEmpty()) {
            throw new IOException("Missing required field in service account JSON: " + key);
        }
        return v;
    }

    private static String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
