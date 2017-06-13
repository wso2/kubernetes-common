## Kubernetes Membership Scheme

Kubernetes membership scheme provides features for automatically discovering WSO2 Carbon server clusters on Kubernetes.

### How It Works
Once a Carbon server starts it will query container IP addresses in the given cluster via Kubernetes API using the given Kubernetes services. Thereafter Hazelcast network configuration will be updated with the above IP addresses. As a result the Hazelcast instance will get connected all the other members in the cluster. In addition once a new member is added to the cluster, all the other members will get connected to the new member.

### Installation

1. For Kubernetes Membership Scheme to work, Hazelcast configuration should be made pluggable. This has to be enabled in the products in different ways. For WSO2 products that are based on Carbon 4.2.0, [apply kernel patch0012](https://docs.wso2.com/display/Carbon420/Applying+a+Patch+to+the+Kernel). For Carbon 4.4.1 based products apply [patch0005](http://product-dist.wso2.com/downloads/carbon/4.4.1/patch0005/WSO2-CARBON-PATCH-4.4.1-0005.zip). These patches include a modification in the Carbon Core component for
allowing to add third party membership schemes. WSO2 products that are based on Carbon versions later than 4.4.1 do not need any patches to be applied (To determine the Carbon version of a particular product, please refer to the [WSO2 Release Matrix](http://wso2.com/products/carbon/release-matrix/)).

2. Copy following JAR files to the `repository/components/dropins` directory of the Carbon server:
   ```
      kubernetes-membership-scheme-1.0.0.jar
   ```

3. Update `axis2.xml` with the following configuration: Please note that you don't need to change localMemberHost value as it will be read from API call.

   ```xml
   <clustering class="org.wso2.carbon.core.clustering.hazelcast.HazelcastClusteringAgent"
                   enable="true">
   
         
           <parameter name="AvoidInitiation">true</parameter>
   
           <parameter name="membershipScheme">kubernetes</parameter>
           <parameter name="domain">pub.store.am.wso2.domain</parameter>
   
           <parameter name="mcastPort">45564</parameter>
           <parameter name="mcastTTL">100</parameter>
           <parameter name="mcastTimeout">60</parameter>
   
           <parameter name="localMemberHost">172.17.0.2</parameter>
           <parameter name="localMemberPort">4000</parameter>
   
           <!--
           Properties specific to this member
           -->
           <parameter name="properties">
               <property name="backendServerURL" value="https://${hostName}:${httpsPort}/services/"/>
               <property name="mgtConsoleURL" value="https://${hostName}:${httpsPort}/"/>
               <property name="subDomain" value="worker"/>
           </parameter>
   
           <parameter name="membershipSchemeClassName">org.wso2.carbon.membership.scheme.kubernetes.KubernetesMembershipScheme</parameter>
           <parameter name="KUBERNETES_NAMESPACE">wso2-demo</parameter>
           <parameter name="KUBERNETES_SERVICES">store,publisher</parameter>
           <parameter name="KUBERNETES_MASTER_SKIP_SSL_VERIFICATION">true</parameter>
   
           <groupManagement enable="false">
               <applicationDomain name="wso2.apim.domain"
                                  description="APIM group"
                           ``       agent="org.wso2.carbon.core.clustering.hazelcast.HazelcastGroupManagementAgent"
                                  subDomain="worker"
                                  port="2222"/>
           </groupManagement>
       </clustering>
```

### Clustering Parameters
1. `KUBERNETES_MASTER` - Kubernetes API endpoint, **ex:** `http://172.17.8.101:8080`
2. `KUBERNETES_MASTER_USERNAME` - Kubernetes Master username (optional), **ex:** `admin`
3. `KUBERNETES_MASTER_PASSWORD` - Kubernetes Master password (optional), **ex:** `admin`
4. `KUBERNETES_NAMESPACE` - Kubernetes Namespace in which the pods are deployed, **ex:** `default`
5. `KUBERNETES_SERVICES` - Kubernetes Services that belong in the cluster, **ex:** `wso2am-gateway`
6. `KUBERNETES_MASTER_SKIP_SSL_VERIFICATION ` - Skip SSL certificate verification of the Kubernetes API (development option), **ex:** `true`
