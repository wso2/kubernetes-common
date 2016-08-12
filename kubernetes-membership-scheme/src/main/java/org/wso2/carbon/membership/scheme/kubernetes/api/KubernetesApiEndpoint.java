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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public abstract class KubernetesApiEndpoint {

    private static final Log log = LogFactory.getLog(KubernetesApiEndpoint.class);

    URL url;
    HttpURLConnection connection;

    KubernetesApiEndpoint(URL url) {
        this.url = url;
    }

    public abstract void createConnection() throws IOException;

    public abstract void createConnection(String username, String password) throws IOException;

    public InputStream read() throws IOException {
        return connection.getInputStream();
    }

    public abstract void disconnect();

    void createBasicAuthenticationHeader(String username, String password) {
        log.debug("Generating basic auth header...");
        String userpass = username + ":" + password;
        String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.
                printBase64Binary(userpass.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", basicAuth);
        log.debug("Basic auth header generated");
    }
}
