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

import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.HazelcastInstance;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.ClusteringMessage;
import org.apache.axis2.description.Parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastCarbonClusterImpl;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastMembershipScheme;
import org.wso2.carbon.core.clustering.hazelcast.HazelcastUtil;
import org.wso2.carbon.membership.scheme.kubernetes.exceptions.KubernetesMembershipSchemeException;
import org.wso2.carbon.membership.scheme.kubernetes.resolver.AddressResolver;
import org.wso2.carbon.membership.scheme.kubernetes.resolver.ApiBasedPodIpResolver;
import org.wso2.carbon.membership.scheme.kubernetes.resolver.DNSBasedPodIpResolver;
import org.wso2.carbon.utils.xml.StringUtils;

import java.net.Inet4Address;
import java.util.*;

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
    private AddressResolver podIpResolver;

    public KubernetesMembershipScheme(Map<String, Parameter> parameters, String primaryDomain, Config config,
            HazelcastInstance primaryHazelcastInstance, List<ClusteringMessage> messageBuffer) {
        this.parameters = parameters;
        this.primaryHazelcastInstance = primaryHazelcastInstance;
        this.messageBuffer = messageBuffer;
        this.nwConfig = config.getNetworkConfig();
    }

    @Override public void setPrimaryHazelcastInstance(HazelcastInstance primaryHazelcastInstance) {
        this.primaryHazelcastInstance = primaryHazelcastInstance;
    }

    @Override public void setLocalMember(Member localMember) {
    }

    @Override public void setCarbonCluster(HazelcastCarbonClusterImpl hazelcastCarbonCluster) {
        this.carbonCluster = hazelcastCarbonCluster;
    }

    /**
     * Retrieves the set of IP addresses of the current PODs in the K8S cluster, using K8S API server or the DNS.
     * The returned set may contain the IP address of the pod running this JVM.
     *
     * The IP address resolver will use DNS lookup if "USE_DNS" environment property is set. Otherwise it uses K8S
     * API server, to retrive the IP addresses.
     *
     * @return IP addresses of the current pods. Returns null upon any error querying K8S.
     */
    private Set<String> getK8sPodIpAddresses() throws KubernetesMembershipSchemeException {
        Set<String> containerIps = podIpResolver.resolveAddresses();
        if (containerIps != null) {
            return containerIps;
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Initiates the Pod IP resolver.
     * Uses the DNS based pod IP resolver or the API based pod IP resolver.
     */
    private void initPodIpResolver() throws KubernetesMembershipSchemeException {
        String useDns = System.getenv(Constants.USE_DNS);
        if (StringUtils.isEmpty(useDns)) {
            useDns = getParameterValue(Constants.USE_DNS, "true");
        }
        if (Boolean.parseBoolean(useDns)) {
            log.debug("Using DNS based pod ip resolving method");
            podIpResolver = new DNSBasedPodIpResolver(parameters);
        } else {
            log.debug("Using API based pod ip resolving method");
            podIpResolver = new ApiBasedPodIpResolver(parameters);
        }
    }

    @Override public void init() throws ClusteringFault {
        try {
            log.info("Initializing kubernetes membership scheme...");
            nwConfig.getJoin().getMulticastConfig().setEnabled(false);
            nwConfig.getJoin().getAwsConfig().setEnabled(false);
            TcpIpConfig tcpIpConfig = nwConfig.getJoin().getTcpIpConfig();
            tcpIpConfig.setEnabled(true);
            initPodIpResolver();
            Set<String> containerIPs = getK8sPodIpAddresses();
            // if no IPs are found, can't initialize clustering
            if (containerIPs.isEmpty()) {
                throw new KubernetesMembershipSchemeException("No members found, unable to initialize the "
                        + "Kubernetes membership scheme");
            }

            for (String containerIP : containerIPs) {
                if (!containerIP.equals(Inet4Address.getLocalHost().getHostAddress())) {
                    tcpIpConfig.addMember(containerIP);
                    log.info("Member added to cluster configuration: [container-ip] " + containerIP);
                }
            }
            log.info("Kubernetes membership scheme initialized successfully");
        } catch (Exception e) {
            String errorMsg = "Kubernetes membership initialization failed";
            log.error(errorMsg, e);
            throw new ClusteringFault(errorMsg, e);
        }
    }

    private String getParameterValue(String parameterName, String defaultValue) throws
            KubernetesMembershipSchemeException {
        Parameter kubernetesServicesParam = getParameter(parameterName);
        if (kubernetesServicesParam == null) {
            if (defaultValue == null) {
                throw new KubernetesMembershipSchemeException(parameterName + " parameter not found");
            } else {
                return defaultValue;
            }
        }
        return (String) kubernetesServicesParam.getValue();
    }

    @Override public void joinGroup() {
        primaryHazelcastInstance.getCluster().addMembershipListener(new KubernetesMembershipSchemeListener());
    }

    private Parameter getParameter(String name) {
        return parameters.get(name);
    }

    /**
     * Kubernetes membership scheme listener
     */
    private class KubernetesMembershipSchemeListener implements MembershipListener {

        @Override public void memberAdded(MembershipEvent membershipEvent) {
            Member member = membershipEvent.getMember();
            TcpIpConfig tcpIpConfig = nwConfig.getJoin().getTcpIpConfig();
            List<String> memberList = tcpIpConfig.getMembers();
            if (!memberList.contains(member.getSocketAddress().getAddress().getHostAddress())) {
                tcpIpConfig.addMember(String.valueOf(member.getSocketAddress().getAddress().getHostAddress()));
            }

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
            if (log.isDebugEnabled()) {
                log.debug(String.format("Current member list: %s", tcpIpConfig.getMembers()));
            }
        }

        @Override public void memberRemoved(MembershipEvent membershipEvent) {
            Member member = membershipEvent.getMember();
            carbonCluster.memberRemoved(member);
            TcpIpConfig tcpIpConfig = nwConfig.getJoin().getTcpIpConfig();
            Set<String> containerIPs;
            String memberIp = member.getSocketAddress().getAddress().getHostAddress();
            try {
                containerIPs = getK8sPodIpAddresses();
                if (!containerIPs.contains(memberIp)) {
                    tcpIpConfig.getMembers()
                            .remove(String.valueOf(member.getSocketAddress().getAddress().getHostAddress()));
                    log.info(String.format("Member left: [UUID] %s, [Address] %s", member.getUuid(),
                            member.getSocketAddress().toString()));
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Current member list: %s", tcpIpConfig.getMembers()));
                    }
                }
            } catch (KubernetesMembershipSchemeException e) {
                log.error("Could not remove member: " + memberIp, e);
            }
        }
    }
}
