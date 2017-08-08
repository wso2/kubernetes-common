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

package org.wso2.carbon.membership.scheme.kubernetes;

public class Constants {

    public static final String BEARER_TOKEN_FILE_LOCATION = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String PARAMETER_NAME_KUBERNETES_API_SERVER = "KUBERNETES_API_SERVER";
    public static final String PARAMETER_NAME_KUBERNETES_API_SERVER_USERNAME = "KUBERNETES_API_SERVER_USERNAME";
    public static final String PARAMETER_NAME_KUBERNETES_API_SERVER_PASSWORD = "KUBERNETES_API_SERVER_PASSWORD";
    public static final String PARAMETER_NAME_KUBERNETES_NAMESPACE = "KUBERNETES_NAMESPACE";
    public static final String PARAMETER_NAME_KUBERNETES_SERVICES = "KUBERNETES_SERVICES";
    public static final String PARAMETER_NAME_KUBERNETES_MASTER_SKIP_SSL_VERIFICATION = "KUBERNETES_MASTER_SKIP_SSL_VERIFICATION";
    public static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
    public static final String KUBERNETES_SERVICE_PORT_HTTPS = "KUBERNETES_SERVICE_PORT_HTTPS";
    public static final String ENDPOINTS_API_CONTEXT = "/api/v1/namespaces/%s/endpoints/";
    public static final String PROTOCOL_HTTPS = "https";
    public static final String DNS_LOOKUP_TIMEOUT = "DNS_LOOKUP_TIMEOUT";
    public static final String USE_DNS = "USE_DNS";
}
