/*
* Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.membership.scheme.kubernetes.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.axis2.description.Parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.membership.scheme.kubernetes.Constants;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * class responsible for resolving pod ips based on Kubernetes API
 */
public class ApiBasedPodIpResolver extends AddressResolver {

    private static final Log log = LogFactory.getLog(ApiBasedPodIpResolver.class);
    private String kubernetesApiServerUrl;
    private String kubernetesMasterUsername;
    private String kubernetesMasterPassword;
    private boolean skipMasterSSLVerification = false;

    public ApiBasedPodIpResolver (final Map<String, Parameter> parameters) throws KubernetesMembershipSchemeException {
        super(parameters);
        initialize();
    }

    private void initialize () throws KubernetesMembershipSchemeException {

        kubernetesApiServerUrl = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER);
        kubernetesMasterUsername = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER_USERNAME);
        kubernetesMasterPassword = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_API_SERVER_PASSWORD);
        String skipMasterVerificationValue = System.getenv(Constants
                .PARAMETER_NAME_KUBERNETES_MASTER_SKIP_SSL_VERIFICATION);

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
                    throw new KubernetesMembershipSchemeException(String.format("Environment variable %s not found",
                            Constants.KUBERNETES_SERVICE_HOST));
                }
                String kubernetesServiceHttpsPortStr = System.getenv(Constants.KUBERNETES_SERVICE_PORT_HTTPS);
                if (StringUtils.isEmpty(kubernetesServiceHttpsPortStr)) {
                    throw new KubernetesMembershipSchemeException(String.format("Environment variable %s not found",
                            Constants.KUBERNETES_SERVICE_PORT_HTTPS));
                }
                int kubernetesServiceHttpsPort = Integer.parseInt(kubernetesServiceHttpsPortStr);
                try {
                    kubernetesApiServerUrl = new URL(Constants.PROTOCOL_HTTPS, kubernetesServiceHost,
                            kubernetesServiceHttpsPort, "").toString();
                } catch (MalformedURLException e) {
                    throw new KubernetesMembershipSchemeException("Kuberneretes master API url: "
                            + kubernetesApiServerUrl + " is malformed", e);
                }
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
                kubernetesApiServerUrl, getKubernetesNamespace(), getKubernetesServices(), skipMasterSSLVerification));
    }

    @Override
    public Set<String> resolveAddresses() throws KubernetesMembershipSchemeException {

        final String apiContext = String.format(Constants.ENDPOINTS_API_CONTEXT, getKubernetesNamespace());
        final Set<String> containerIPs = new HashSet<>();

        for (String kubernetesService : getKubernetesServicesArray()) {
            // Create k8s api endpoint URL
            URL apiEndpointUrl = createUrl(kubernetesApiServerUrl, apiContext + kubernetesService.trim());

            // Create http/https k8s api endpoint
            KubernetesApiEndpoint apiEndpoint = createAPIEndpoint(apiEndpointUrl);

            // Create the connection and read k8s service endpoints
            Endpoints endpoints;
            try {
                endpoints = getEndpoints(connectAndRead
                        (apiEndpoint, kubernetesMasterUsername, kubernetesMasterPassword));

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

    public String getKubernetesApiServerUrl() {
        return kubernetesApiServerUrl;
    }

    public String getKubernetesMasterUsername() {
        return kubernetesMasterUsername;
    }

    public String getKubernetesMasterPassword() {
        return kubernetesMasterPassword;
    }

    public boolean isSkipMasterSSLVerification() {
        return skipMasterSSLVerification;
    }
}
