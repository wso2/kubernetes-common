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

import org.apache.axis2.description.Parameter;
import org.wso2.carbon.membership.scheme.kubernetes.Constants;
import org.wso2.carbon.membership.scheme.kubernetes.exceptions.KubernetesMembershipSchemeException;
import org.wso2.carbon.utils.xml.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * Abstraction for resolving networks addresses
 */
public abstract class AddressResolver {

    private final Map<String, Parameter> parameters;
    private String kubernetesNamespace;
    private String kubernetesServices;
    private String[] kubernetesServicesArray;

    AddressResolver (final Map<String, Parameter> parameters) throws KubernetesMembershipSchemeException {
        this.parameters = parameters;
        initialize();
    }

    /**
     * Initialize AddressResolver instance
     *
     * @throws KubernetesMembershipSchemeException if an error occurred while initializing
     */
    private void initialize () throws KubernetesMembershipSchemeException {
        kubernetesNamespace = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_NAMESPACE);
        kubernetesServices = System.getenv(Constants.PARAMETER_NAME_KUBERNETES_SERVICES);

        if (StringUtils.isEmpty(kubernetesNamespace)) {
            kubernetesNamespace = getParameterValue(Constants.PARAMETER_NAME_KUBERNETES_NAMESPACE, "default");
        }

        if (StringUtils.isEmpty(kubernetesServices)) {
            kubernetesServices = getParameterValue(Constants.PARAMETER_NAME_KUBERNETES_SERVICES, null);
            if (StringUtils.isEmpty(kubernetesServices)) {
                throw new KubernetesMembershipSchemeException("Kubernetes services parameter not found");
            }
        }

        // split the provided comma separated service names
        kubernetesServicesArray = kubernetesServices.split(",");
    }

    /**
     * Resolve the addresses of the members
     *
     * @return {@link Set} of addresses
     * @throws KubernetesMembershipSchemeException if an error occurred while resolving the addresses
     */
    public abstract Set<String> resolveAddresses () throws KubernetesMembershipSchemeException;

    String getParameterValue(String parameterName, String defaultValue)
            throws KubernetesMembershipSchemeException {
        Parameter kubernetesServicesParam = parameters.get(parameterName);
        if (kubernetesServicesParam == null) {
            if (defaultValue == null) {
                throw new KubernetesMembershipSchemeException(parameterName + " parameter not found");
            } else {
                return defaultValue;
            }
        }
        return (String) kubernetesServicesParam.getValue();
    }

    public Map<String, Parameter> getParameters() {
        return parameters;
    }

    public String getKubernetesNamespace() {
        return kubernetesNamespace;
    }

    public String getKubernetesServices() {
        return kubernetesServices;
    }

    public String[] getKubernetesServicesArray() {
        return kubernetesServicesArray;
    }
}
