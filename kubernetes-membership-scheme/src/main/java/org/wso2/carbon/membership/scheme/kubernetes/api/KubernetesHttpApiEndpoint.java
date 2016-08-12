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
import java.net.HttpURLConnection;
import java.net.URL;

public class KubernetesHttpApiEndpoint extends KubernetesApiEndpoint {

    private static final Log log = LogFactory.getLog(KubernetesHttpApiEndpoint.class);

    public KubernetesHttpApiEndpoint(URL url) {
        super(url);
    }

    @Override
    public void createConnection() throws IOException {
        log.debug("Connecting to Kubernetes API server...");
        connection = (HttpURLConnection) url.openConnection();
        log.debug("Connected successfully");
    }

    @Override
    public void createConnection(String username, String password) throws IOException {
        log.debug("Connecting to Kubernetes API server with basic auth...");
        connection = (HttpURLConnection) url.openConnection();
        createBasicAuthenticationHeader(username, password);
        log.debug("Connected successfully");
    }

    @Override
    public void disconnect() {
        log.debug("Disconnecting from Kubernetes API server...");
        connection.disconnect();
        log.debug("Disconnected successfully");
    }
}
