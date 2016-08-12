/*
* Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.wso2.carbon.membership.scheme.kubernetes.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.membership.scheme.kubernetes.Constants;
import org.wso2.carbon.utils.xml.StringUtils;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class KubernetesHttpsApiEndpoint extends KubernetesApiEndpoint {

    private static final Log log = LogFactory.getLog(KubernetesHttpsApiEndpoint.class);

    public KubernetesHttpsApiEndpoint(URL url, boolean skipMasterSSLVerification) {
        super(url);
        if (skipMasterSSLVerification) {
            disableCertificateValidation();
        }
    }

    @Override
    public void createConnection() throws IOException {
        log.debug("Connecting to Kubernetes API server...");
        connection = (HttpsURLConnection) url.openConnection();
        connection.addRequestProperty(Constants.AUTHORIZATION_HEADER, "Bearer " + getServiceAccountToken());
        log.debug("Connected successfully");
    }

    @Override
    public void createConnection(String username, String password) throws IOException {
        log.debug("Connecting to Kubernetes API server with basic auth...");
        connection = (HttpsURLConnection) url.openConnection();
        createBasicAuthenticationHeader(username, password);
        log.debug("Connected successfully");
    }


    @Override
    public void disconnect() {
        log.debug("Disconnecting from Kubernetes API server...");
        connection.disconnect();
        log.debug("Disconnected successfully");
    }

    private static void disableCertificateValidation() {

        TrustManager[] dummyTrustMgr = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // do nothing
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // do nothing
                    }
                }};

        // Ignore differences between given hostname and certificate hostname
        HostnameVerifier dummyHostVerifier = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                // always true
                return true;
            }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, dummyTrustMgr, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(dummyHostVerifier);
        } catch (NoSuchAlgorithmException | KeyManagementException ignored) {
        }
    }

    private String getServiceAccountToken() throws IOException {
        String bearerTokenFileLocation = System.getenv("BEARER_TOKEN_FILE_LOCATION");
        if (StringUtils.isEmpty(bearerTokenFileLocation)) {
            bearerTokenFileLocation = Constants.BEARER_TOKEN_FILE_LOCATION;
        }
        return new String(Files.readAllBytes(Paths.get(bearerTokenFileLocation)), StandardCharsets.UTF_8);
    }
}
