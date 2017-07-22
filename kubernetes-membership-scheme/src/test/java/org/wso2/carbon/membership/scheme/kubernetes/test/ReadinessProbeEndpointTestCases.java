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

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.axis2.clustering.ClusteringMessage;
import org.apache.axis2.description.Parameter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.wso2.carbon.membership.scheme.kubernetes.Constants;
import org.wso2.carbon.membership.scheme.kubernetes.KubernetesMembershipScheme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.testng.Assert.assertEquals;
import static org.wso2.carbon.membership.scheme.kubernetes.test.KubernetesAPIMockServer.MOCK_SERVER_PORT;

/**
 * Integration tests for kubernetes membership scheme
 */
public class ReadinessProbeEndpointTestCases {
    private static final Log log = LogFactory.getLog(ReadinessProbeEndpointTestCases.class);
    private ClientAndServer mockServer;
    private String namespace = "default";
    private String services = "wso2is-default";

    @BeforeTest
    public void init() throws Exception {
        mockServer = KubernetesAPIMockServer.getMockServer();

    }

    @Test
    public void testEndpointAddressesOnly() throws Exception {
        // test Endpoint with Addresses Only
        log.info("Executing test case with Addresses Only ");
        registerAPI("/ReadinessProbeEndpointTestCases/addressOnly.json");
        assertEquals(initializeMembershipScheme().size(), 2, "Endpoint with Addresses Only");
    }

    @Test
    public void testEndpointNotReadyAddressesOnly() throws Exception {
        // test Endpoint with notReadyAddresses Only
        log.info("Executing test case with notReadyAddresses Only ");
        registerAPI("/ReadinessProbeEndpointTestCases/notReadyAddressOnly.json");
        assertEquals(initializeMembershipScheme().size(), 2, "Endpoint with notReadyAddresses Only");

    }

    @Test
    public void testEndpointNotReadyAddressesAndAddresses() throws Exception {
        // test Endpoint with NotReadyAddresses And Addresses
        log.info("Executing test case NotReadyAddresses And Addresses");
        registerAPI("/ReadinessProbeEndpointTestCases/notReadyAddressAndAddress.json");
        assertEquals(initializeMembershipScheme().size(), 2, "Endpoint with notReadyAddress And Address");

    }

    private void registerAPI(String endpointJSONPath) throws IOException {
        // Registering request and response with mock server
        String endpointJSON = IOUtils.toString(
                this.getClass().getResourceAsStream(endpointJSONPath), "UTF-8");
        mockServer.when(
                request()
                        .withPath(String.format(Constants.ENDPOINTS_API_CONTEXT, namespace)
                                + services)
                        .withMethod("GET")
        ).respond(
                response()
                        .withStatusCode(202)
                        .withHeaders(
                                new Header("Content-Type", "application/json; charset=utf-8")
                        )
                        .withBody(endpointJSON)
        );
    }

    private List<String> initializeMembershipScheme() throws Exception {
        //Initializing membership scheme
        Config primaryHazelcastConfig;
        HazelcastInstance primaryHazelcastInstance;
        Map<String, Parameter> parameters = new HashMap<>();
        List<ClusteringMessage> messageBuffer;
        String primaryDomain = "TestDomain";
        parameters.put(Constants.PARAMETER_NAME_KUBERNETES_NAMESPACE,
                new Parameter(Constants.PARAMETER_NAME_KUBERNETES_NAMESPACE, namespace));
        parameters.put(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER,
                new Parameter(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER,
                        "http://localhost:" + MOCK_SERVER_PORT));
        parameters.put(Constants.PARAMETER_NAME_KUBERNETES_SERVICES,
                new Parameter(Constants.PARAMETER_NAME_KUBERNETES_SERVICES, services));
        parameters.put(Constants.USE_DNS, new Parameter(Constants.USE_DNS, "false"));

        messageBuffer = new ArrayList<>();
        primaryHazelcastConfig = new Config();
        primaryHazelcastInstance = Hazelcast.newHazelcastInstance(primaryHazelcastConfig);
        KubernetesMembershipScheme kubernetesMembershipScheme = new KubernetesMembershipScheme
                (parameters, primaryDomain, primaryHazelcastConfig, primaryHazelcastInstance,
                        messageBuffer);
        kubernetesMembershipScheme.init();
        log.info("Membership scheme initialized");
        return primaryHazelcastConfig.getNetworkConfig().getJoin().
                getTcpIpConfig().getMembers();

    }
}
