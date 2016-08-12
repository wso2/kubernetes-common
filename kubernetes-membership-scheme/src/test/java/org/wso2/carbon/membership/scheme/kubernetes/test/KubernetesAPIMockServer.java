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
package org.wso2.carbon.membership.scheme.kubernetes.test;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mockserver.integration.ClientAndServer;
import org.testng.annotations.AfterSuite;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * KubernetesAPI Mock Server for integration tests
 */
public class KubernetesAPIMockServer {

    private static final Log log = LogFactory.getLog(KubernetesAPIMockServer.class);
    private static ClientAndServer mockServer;
    static final int MOCK_SERVER_PORT = 9090;

    static ClientAndServer getMockServer() {
        if (mockServer == null) {
            mockServer = startClientAndServer(MOCK_SERVER_PORT);
            log.info("Mock API server started at port: " + MOCK_SERVER_PORT);
        }
        return mockServer;
    }

    @AfterSuite
    private void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }
}
