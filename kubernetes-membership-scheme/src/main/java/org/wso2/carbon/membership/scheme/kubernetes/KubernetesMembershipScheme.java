/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.membership.scheme.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.*;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.ClusteringMessage;
import org.apache.axis2.description.Parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastCarbonClusterImpl;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastMembershipScheme;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastUtil;
import org.wso2.carbon.membership.scheme.kubernetes.api.KubernetesApiEndpoint;
import org.wso2.carbon.membership.scheme.kubernetes.api.KubernetesHttpApiEndpoint;
import org.wso2.carbon.membership.scheme.kubernetes.api.KubernetesHttpsApiEndpoint;
import org.wso2.carbon.membership.scheme.kubernetes.domain.Address;
import org.wso2.carbon.membership.scheme.kubernetes.domain.Endpoints;
import org.wso2.carbon.membership.scheme.kubernetes.domain.Subset;
import org.wso2.carbon.membership.scheme.kubernetes.exceptions.KubernetesMembershipSchemeException;
import org.wso2.carbon.utils.xml.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes membership scheme provides carbon cluster discovery on kubernetes.
 */
public class KubernetesMembershipScheme implements HazelcastMembershipScheme {

    private static final Log log = LogFactory.getLog(KubernetesMembershipScheme.class);

    private final Map<String, Parameter> parameters;
    private final NetworkConfig nwConfig;
    private final List<ClusteringMessage> messageBuffer;
    private HazelcastInstance primaryHazelcastInstance;
    private HazelcastCarbonClusterImpl carbonCluster;
    private boolean skipMasterSSLVerification;

    public KubernetesMembershipScheme(Map<String, Parameter> parameters,
                                      String primaryDomain,
                                      Config config,
                                      HazelcastInstance primaryHazelcastInstance,
                                      List<ClusteringMessage> messageBuffer) {
        this.parameters = parameters;
        this.primaryHazelcastInstance = primaryHazelcastInstance;
        this.messageBuffer = messageBuffer;
        this.nwConfig = config.getNetworkConfig();
    }

    @Override
    public void setPrimaryHazelcastInstance(HazelcastInstance primaryHazelcastInstance) {
        this.primaryHazelcastInstance = primaryHazelcastInstance;
    }

    @Override
    public void setLocalMember(Member localMember) {
    }

    @Override
    public void setCarbonCluster(HazelcastCarbonClusterImpl hazelcastCarbonCluster) {
        this.carbonCluster = hazelcastCarbonCluster;
    }

    @Override
    public void init() throws ClusteringFault {
        try {
            log.info("Initializing kubernetes membership scheme...");

            nwConfig.getJoin().getMulticastConfig().setEnabled(false);
            nwConfig.getJoin().getAwsConfig().setEnabled(false);
            TcpIpConfig tcpIpConfig = nwConfig.getJoin().getTcpIpConfig();
            tcpIpConfig.setEnabled(true);

            // Try to read parameters from env variables
            String kubernetesApiServerUrl = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER);
            String kubernetesNamespace = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_NAMESPACE);
            String kubernetesServices = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_SERVICES);
            String kubernetesMasterUsername = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER_USERNAME);
            String kubernetesMasterPassword = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER_PASSWORD);
            String skipMasterVerificationValue = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_MASTER_SKIP_SSL_VERIFICATION);

            // If not available read from clustering configuration
            if (StringUtils.isEmpty(kubernetesApiServerUrl)) {
                kubernetesApiServerUrl = getParameterValue(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER, "");
                if (StringUtils.isEmpty(kubernetesApiServerUrl)) {
                    log.info(String.format("Parameter %s not found, checking %s & %s",
                            Constants.PARAMETER_NAME_KUBERNETES_API_SERVER,
                            Constants.KUBERNETES_SERVICE_HOST,
                            Constants.KUBERNETES_SERVICE_PORT_HTTPS));

                    String kubernetesServiceHost = System.getenv(Constants.KUBERNETES_SERVICE_HOST);
                    if (StringUtils.isEmpty(kubernetesServiceHost)) {
                        throw new ClusteringFault(String.format("Environment variable %s not found",
                                Constants.KUBERNETES_SERVICE_HOST));
                    }
                    String kubernetesServiceHttpsPortStr = System.getenv(Constants.KUBERNETES_SERVICE_PORT_HTTPS);
                    if (StringUtils.isEmpty(kubernetesServiceHttpsPortStr)) {
                        throw new ClusteringFault(String.format("Environment variable %s not found",
                                Constants.KUBERNETES_SERVICE_PORT_HTTPS));
                    }
                    int kubernetesServiceHttpsPort = Integer.parseInt(kubernetesServiceHttpsPortStr);
                    kubernetesApiServerUrl = new URL(Constants.PROTOCOL_HTTPS, kubernetesServiceHost, kubernetesServiceHttpsPort, "").toString();
                }
            }

            if (StringUtils.isEmpty(kubernetesNamespace)) {
                kubernetesNamespace = getParameterValue(Constants.PARAMETER_NAME_KUBERNETES_NAMESPACE, "default");
            }

            if (StringUtils.isEmpty(kubernetesServices)) {
                kubernetesServices = getParameterValue(Constants.PARAMETER_NAME_KUBERNETES_SERVICES);
                if (StringUtils.isEmpty(kubernetesServices)) {
                    throw new ClusteringFault("Kubernetes services parameter not found");
                }
            }

            if (StringUtils.isEmpty(kubernetesMasterUsername)) {
                kubernetesMasterUsername = getParameterValue(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER_USERNAME, "");
            }

            if (StringUtils.isEmpty(kubernetesMasterPassword)) {
                kubernetesMasterPassword = getParameterValue(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER_PASSWORD, "");
            }

            if (StringUtils.isEmpty(skipMasterVerificationValue)) {
                skipMasterVerificationValue = getParameterValue(Constants.PARAMETER_NAME_KUBERNETES_MASTER_SKIP_SSL_VERIFICATION, "false");
            }

            skipMasterSSLVerification = Boolean.parseBoolean(skipMasterVerificationValue);

            log.info(String.format("Kubernetes clustering configuration: [api-server] %s [namespace] %s [services] %s [skip-master-ssl-verification] %s",
                    kubernetesApiServerUrl, kubernetesNamespace, kubernetesServices, skipMasterSSLVerification));

            String[] kubernetesServicesArray = kubernetesServices.split(",");
            for (String kubernetesService : kubernetesServicesArray) {
                List<String> containerIPs = findContainerIPs(kubernetesApiServerUrl,
                        kubernetesNamespace, kubernetesService, kubernetesMasterUsername, kubernetesMasterPassword);
                for (String containerIP : containerIPs) {
                    tcpIpConfig.addMember(containerIP);
                    log.info("Member added to cluster configuration: [container-ip] " + containerIP);
                }
            }
            log.info("Kubernetes membership scheme initialized successfully");
        } catch (Exception e) {
            log.error(e);
            throw new ClusteringFault("Kubernetes membership initialization failed", e);
        }
    }

    private String getParameterValue(String parameterName) throws ClusteringFault {
        return getParameterValue(parameterName, null);
    }

    private String getParameterValue(String parameterName, String defaultValue) throws ClusteringFault {
        Parameter kubernetesServicesParam = getParameter(parameterName);
        if (kubernetesServicesParam == null) {
            if (defaultValue == null) {
                throw new ClusteringFault(parameterName + " parameter not found");
            } else {
                return defaultValue;
            }
        }
        return (String) kubernetesServicesParam.getValue();
    }

    private List<String> findContainerIPs(String kubernetesMaster, String namespace, String serviceName,
                                          String username, String password) throws KubernetesMembershipSchemeException {

        final String apiContext = String.format(Constants.ENDPOINTS_API_CONTEXT, namespace);
        final List<String> containerIPs = new ArrayList<>();

        // Create k8s api endpoint URL
        URL apiEndpointUrl = createUrl(kubernetesMaster, apiContext + serviceName);

        // Create http/https k8s api endpoint
        KubernetesApiEndpoint apiEndpoint = createAPIEndpoint(apiEndpointUrl);

        // Create the connection and read k8s service endpoints
        Endpoints endpoints = null;
        try {
            endpoints = getEndpoints(connectAndRead(apiEndpoint, username, password));

        } catch (IOException e) {
            throw new KubernetesMembershipSchemeException("Could not get the Endpoints", e);

        } finally {
            apiEndpoint.disconnect();
        }

        if (endpoints != null) {
            if (endpoints.getSubsets() != null && !endpoints.getSubsets().isEmpty()) {
                // Reading IP addresses from two lists
                log.info("Reading IP addresses from endpoints");
                for (Subset subset : endpoints.getSubsets()) {
                    if (subset.getAddresses() != null) {
                        for (Address address : subset.getAddresses()) {
                            containerIPs.add(address.getIp());
                        }
                    }
                    if (subset.getNotReadyAddresses() != null) {
                        for (Address address : subset.getNotReadyAddresses()) {
                            containerIPs.add(address.getIp());
                        }
                    }
                }
            }
        } else {
            log.info("No endpoints found at " + apiEndpointUrl.toString());
        }
        return containerIPs;
    }

    private URL createUrl(String master, String context)
            throws KubernetesMembershipSchemeException {

        // concatenate and generate the String url
        if (master.endsWith("/")) {
            master = master.substring(0, master.length() - 1);
        }

        URL apiEndpointUrl;
        try {
            apiEndpointUrl = new URL(master + context);
            if (log.isDebugEnabled()) {
                log.debug("Resource location: " + master + context);
            }
        } catch (IOException e) {
            throw new KubernetesMembershipSchemeException("Could not construct Kubernetes API endpoint URL", e);
        }
        return apiEndpointUrl;
    }

    private KubernetesApiEndpoint createAPIEndpoint(URL url) throws KubernetesMembershipSchemeException {

        KubernetesApiEndpoint apiEndpoint;

        if (url.getProtocol().equalsIgnoreCase("https")) {
            apiEndpoint = new KubernetesHttpsApiEndpoint(url, skipMasterSSLVerification);
        } else if (url.getProtocol().equalsIgnoreCase("http")) {
            apiEndpoint = new KubernetesHttpApiEndpoint(url);
        } else {
            throw new KubernetesMembershipSchemeException("K8s master API endpoint is neither HTTP or HTTPS");
        }

        return apiEndpoint;
    }

    private InputStream connectAndRead(KubernetesApiEndpoint endpoint, String
            username, String password) throws KubernetesMembershipSchemeException {

        try {
            // Use basic auth to create the connection if username and password are specified
            if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
                endpoint.createConnection(username, password);
            } else {
                endpoint.createConnection();
            }

        } catch (IOException e) {
            throw new KubernetesMembershipSchemeException("Could not connect to Kubernetes API", e);
        }

        try {
            return endpoint.read();
        } catch (IOException e) {
            throw new KubernetesMembershipSchemeException("Could not connect to Kubernetes API", e);
        }
    }

    private Endpoints getEndpoints(InputStream inputStream) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(inputStream, Endpoints.class);
    }

    @Override
    public void joinGroup() throws ClusteringFault {
        primaryHazelcastInstance.getCluster().addMembershipListener(new KubernetesMembershipSchemeListener());
    }

    private Parameter getParameter(String name) {
        return parameters.get(name);
    }

    /**
     * Kubernetes membership scheme listener
     */
    private class KubernetesMembershipSchemeListener implements MembershipListener {

        @Override
        public void memberAdded(MembershipEvent membershipEvent) {
            Member member = membershipEvent.getMember();

            // Send all cluster messages
            carbonCluster.memberAdded(member);
            log.info(String.format("Member joined: [UUID] %s, [Address] %s", member.getUuid(),
                    member.getSocketAddress().toString()));
            // Wait for sometime for the member to completely join before replaying messages
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            HazelcastUtil.sendMessagesToMember(messageBuffer, member, carbonCluster);
        }

        @Override
        public void memberRemoved(MembershipEvent membershipEvent) {
            Member member = membershipEvent.getMember();
            carbonCluster.memberRemoved(member);
            log.info(String.format("Member left: [UUID] %s, [Address] %s", member.getUuid(),
                    member.getSocketAddress().toString()));
        }

        @Override
        public void memberAttributeChanged(MemberAttributeEvent memberAttributeEvent) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member attribute changed: [Key] %s, [Value] %s",
                        memberAttributeEvent.getKey(),
                        memberAttributeEvent.getValue()));
            }
        }
    }
}