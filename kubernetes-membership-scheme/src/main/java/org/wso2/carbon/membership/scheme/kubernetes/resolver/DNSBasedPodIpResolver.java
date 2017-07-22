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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.membership.scheme.kubernetes.Constants;
import org.wso2.carbon.membership.scheme.kubernetes.exceptions.KubernetesMembershipSchemeException;
import org.wso2.carbon.utils.xml.StringUtils;
import org.xbill.DNS.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Class responsible for resolving pod ips based on DNS lookups
 */
public class DNSBasedPodIpResolver extends AddressResolver {

    private static final Log log = LogFactory.getLog(DNSBasedPodIpResolver.class);
    private int dnsLookupTimeout;

    public DNSBasedPodIpResolver(Map<String, Parameter> parameters) throws KubernetesMembershipSchemeException {
        super(parameters);
        initialize();
    }

    private void initialize () throws KubernetesMembershipSchemeException {
        String dnsLookupTimeoutValue = System.getenv(Constants.DNS_LOOKUP_TIMEOUT);
        if (StringUtils.isEmpty(dnsLookupTimeoutValue)) {
            dnsLookupTimeoutValue = getParameterValue(Constants.DNS_LOOKUP_TIMEOUT, "10");
        }

        dnsLookupTimeout = Integer.parseInt(dnsLookupTimeoutValue);
    }

    @Override
    public Set<String> resolveAddresses() throws KubernetesMembershipSchemeException {

        final Set<String> containerIPs = new HashSet<>();

        for (String kubernetesService : getKubernetesServicesArray()) {
            // lookup name format:
            // <service-name>.<namespace>.svc.cluster.local
            String dnsLookupName = new StringBuilder(kubernetesService.trim()).append(".").
                    append(getKubernetesNamespace()).append(".").append("svc.cluster.local").toString();
            log.info("Going to perform a DNS lookup for: " + dnsLookupName);

            Lookup lookup = buildLookup(dnsLookupName);
            Record[] records = lookup.run();

            if (lookup.getResult() != Lookup.SUCCESSFUL) {
                log.warn("DNS lookup for name '" + dnsLookupName + "' failed");
                return Collections.emptySet();
            }

            for (Record record : records) {
                SRVRecord srv = (SRVRecord) record;
                InetAddress[] inetAddresses = getAddresses(srv);

                for (InetAddress inetAddress : inetAddresses) {
                    log.info("Found IP address " + inetAddress.getHostAddress() + "  for DNS lookup: " +
                            dnsLookupName + ", SRV Record name : " + srv.getName().toString());
                    containerIPs.add(inetAddress.getHostAddress());
                }
            }
        }

        return containerIPs;
    }

    /**
     * Creates the {@link Lookup} instance for the given dnsLookupName
     *
     * @param dnsLookupName lookup name
     * @return {@link Lookup} instance
     * @throws KubernetesMembershipSchemeException if an error occurs while building the Lookup
     */
    private Lookup buildLookup(String dnsLookupName)
            throws KubernetesMembershipSchemeException {

        ExtendedResolver resolver = null;
        try {
            resolver = new ExtendedResolver();
        } catch (UnknownHostException e) {
            throw new KubernetesMembershipSchemeException("Lookup creation error - unknown hostname", e);
        }
        resolver.setTimeout(dnsLookupTimeout);

        Lookup lookup = null;
        try {
            lookup = new Lookup(dnsLookupName, Type.SRV);
        } catch (TextParseException e) {
            throw new KubernetesMembershipSchemeException("Lookup creation error", e);
        }
        lookup.setResolver(resolver);

        // Avoid caching temporary DNS lookup failures indefinitely in global cache
        lookup.setCache(null);

        return lookup;
    }

    /**
     * Creates an array of addresses for the given SRV record
     *
     * @param srv {@link SRVRecord} instance
     * @return array of addresses
     * @throws KubernetesMembershipSchemeException if an error occurs in getting the addresses
     */
    private InetAddress[] getAddresses(SRVRecord srv) throws KubernetesMembershipSchemeException {

        try {
            return org.xbill.DNS.Address.getAllByName(srv.getTarget().canonicalize().toString(true));
        } catch (UnknownHostException e) {
            throw new KubernetesMembershipSchemeException("Parsing DNS records failed", e);
        }
    }

    public int getDnsLookupTimeout() {
        return dnsLookupTimeout;
    }
}
