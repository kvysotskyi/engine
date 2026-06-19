/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dicomweb;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SimpleTimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.client.methods.HttpPost;

/**
 * Signs an HTTP POST request using AWS Signature Version 4 (SigV4).
 *
 * Adds three headers to the request:
 *   X-Amz-Date            – ISO 8601 datetime in UTC
 *   X-Amz-Content-SHA256  – hex SHA-256 of the request body
 *   Authorization         – AWS4-HMAC-SHA256 credential/signature
 *
 * No external AWS SDK required — implemented with JDK's MessageDigest and Mac.
 */
class AwsSigV4Helper {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Signs the given HttpPost in-place.
     *
     * @param post          request already configured with URL, Content-Type, and entity
     * @param body          raw request body bytes (needed to compute payload hash)
     * @param region        AWS region (e.g. "us-east-1")
     * @param service       AWS service name (e.g. "medical-imaging", "execute-api")
     * @param accessKeyId   AWS access key ID
     * @param secretKey     AWS secret access key
     */
    static void sign(HttpPost post, byte[] body, String region, String service,
            String accessKeyId, String secretKey) throws Exception {

        Date now = new Date();
        String datetime = formatDatetime(now);   // e.g. 20240115T120000Z
        String date     = datetime.substring(0, 8); // e.g. 20240115

        URI uri = post.getURI();
        String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");

        String contentType = post.getFirstHeader("Content-Type") != null
                ? post.getFirstHeader("Content-Type").getValue() : "";
        String payloadHash = hex(sha256(body));

        // Set required headers before signing
        post.setHeader("X-Amz-Date", datetime);
        post.setHeader("X-Amz-Content-SHA256", payloadHash);
        post.setHeader("Host", host);

        // Canonical request
        String canonicalPath    = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String canonicalQuery   = uri.getRawQuery() != null ? uri.getRawQuery() : "";
        String canonicalHeaders =
                "content-type:" + contentType + "\n" +
                "host:"         + host        + "\n" +
                "x-amz-content-sha256:" + payloadHash + "\n" +
                "x-amz-date:"   + datetime    + "\n";
        String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";

        String canonicalRequest = "POST\n"
                + canonicalPath    + "\n"
                + canonicalQuery   + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders    + "\n"
                + payloadHash;

        // String to sign
        String credentialScope = date + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = ALGORITHM + "\n"
                + datetime        + "\n"
                + credentialScope + "\n"
                + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        // Signing key: HMAC chain
        byte[] signingKey = deriveSigningKey(secretKey, date, region, service);
        String signature  = hex(hmac(signingKey, stringToSign.getBytes(StandardCharsets.UTF_8)));

        String authHeader = ALGORITHM
                + " Credential=" + accessKeyId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        post.setHeader("Authorization", authHeader);
    }

    // -------------------------------------------------------------------------

    private static byte[] deriveSigningKey(String secretKey, String date, String region,
            String service) throws Exception {
        byte[] kSecret  = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate    = hmac(kSecret,   date.getBytes(StandardCharsets.UTF_8));
        byte[] kRegion  = hmac(kDate,     region.getBytes(StandardCharsets.UTF_8));
        byte[] kService = hmac(kRegion,   service.getBytes(StandardCharsets.UTF_8));
        return            hmac(kService,  "aws4_request".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        return mac.doFinal(data);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String formatDatetime(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        sdf.setTimeZone(new SimpleTimeZone(0, "UTC"));
        return sdf.format(date);
    }
}
