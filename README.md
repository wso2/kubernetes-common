# WSO2 Kubernetes Artifacts

WSO2 Kubernetes Artifacts enable you to run WSO2 products seamlessly on [Kubernetes] (https://kubernetes.io) using Docker. This repository contains artifacts (Service and Replication Controller definitions) to deploy WSO2 products on Kubernetes.

## Getting Started
>In the context of this document, `KUBERNETES_HOME`, `DOCKERFILES_HOME` and `PUPPET_HOME` will refer to local copies of [`wso2/kubernetes artifacts`](https://github.com/wso2/kubernetes-artifacts/), [`wso2/dockcerfiles`](https://github.com/wso2/dockerfiles/) and [`wso2/puppet modules`](https://github.com/wso2/puppet-modules) repositories respectively.

To deploy a WSO2 product on Kubernetes, the following steps have to be done.
* Build relevant Docker images
* Copy the images to the Kubernetes Nodes
* Run `deploy.sh` inside the relevant product folder, which will deploy the Service and the Replication Controllers

##### 1. Build Docker Images

To manage configurations and artifacts when building Docker images, WSO2 recommends to use [`wso2/puppet modules`](https://github.com/wso2/puppet-modules) as the provisioning method. A specific data set for Kubernetes platform is available in WSO2 Puppet Modules. It's possible to use this data set to build Dockerfiles for wso2 products for Kubernetes with minimum configuration changes.

Building WSO2 Docker images using Puppet for Kubernetes:

  1. Clone `wso2/puppet modules` and `wso2/dockerfiles` repositories (alternatively you can download the released artifacts using the release page of the GitHub repository).
  2. Copy the `kubernetes-membership-scheme-1.0.0.jar` to `PUPPET_HOME/modules/<product>/files/configs/repository/components/dropins` location and apply relevant Kernel patches for clustering.
  3. Copy the JDK [`jdk-7u80-linux-x64.tar.gz`](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html) to `PUPPET_HOME/modules/wso2base/files` location.
  4. Copy the [`mysql-connector-java-5.1.36-bin.jar`](http://mvnrepository.com/artifact/mysql/mysql-connector-java/5.1.36) file to `PUPPET_HOME/modules/<product>/files/configs/repository/components/lib` location.
  5. Copy the product zip file to `PUPPET_HOME/modules/wso2{product}/files` location.
  6. Set the environment variable `PUPPET_HOME` pointing to location of the puppet modules in local machine.
  7. Navigate to the relevant product directory in the dockerfiles repository; `DOCKERFILES_HOME/<product>`.
  8. Build the Dockerfile with the following command:

    **`./build.sh -v [product-version] -s kubernetes -r puppet`**

  Note that `-s kubernetes` and `-r puppet` flags denotes the Kubernetes platform and Puppet provisioning method.

  This will build the standalone product for Kubernetes platform, using configuration specified in Puppet. Please note it's possible to build relevant profiles of the products similarly. Refer `build.sh` script usage (`./build.sh -h`).

##### 2. Copy the Images to Kubernetes Nodes/Registry

Copy the required Docker images over to the Kubernetes Nodes (ex: use `docker save` to create a tarball of the required image, `scp` the tarball to each node, and use `docker load` to reload the images from the copied tarballs on the nodes). Alternatively, if a private Docker registry is used, transfer the images there.

You can make use of the `load-images.sh` helper script to transfer images to the Kubernetes nodes. It will search for any Docker images with `wso2` as a part of its name on your local machine, and ask for verification to transfer them to the Kubernetes nodes. `kubectl` has to be functioning on your local machine in order for the script to retrieve the list of Kubernetes nodes. You can optionally provide a search pattern if you want to override the default `wso2` string.

**`load-images.sh`
Usage**
```
Usage: ./load-images.sh [OPTIONS]

Transfer Docker images to Kubernetes Nodes
Options:

  -u	[OPTIONAL] Username to be used to connect to Kubernetes Nodes. If not provided, default "core" is used.
  -p	[OPTIONAL] Optional search pattern to search for Docker images. If not provided, default "wso2" is used.
  -h	[OPTIONAL] Show help text.

Ex: ./load-images.sh
Ex: ./load-images.sh -u ubuntu
Ex: ./load-images.sh -p wso2is
```


##### 3. Deploy Kubernetes Artifacts
  1. Navigate to relevant product directory in kubernetes repository; `KUBERNETES_HOME/<product>` location.
  2. run the deploy.sh script:

    **`./deploy.sh`**

      This will deploy the standalone product in Kubernetes, using the image available in Kubernetes nodes, and notify once the intended service starts running on the pod.
      __Please note that each Kubernetes node needs the [`mysql:5.5`](https://hub.docker.com/_/mysql/) docker image in the node's docker registry.__

##### 4. Access Management Console
  1. Add an host entry (in Linux, using the `/etc/hosts` file) for `<product_name>-default`, resolving to the Kubernetes node IP.
  2. Access the Carbon Management Console URL using `https://<product_name>-default:<node_port>/carbon/`

##### 5. Undeploy
  1. Navigate to relevant product directory in Kubernetes repository; `KUBERNETES_HOME/<product>` location.
  2. run the `undeploy.sh` script:

    **`./undeploy.sh`**

      This will undeploy the product specific DB pod, Kubernetes Replication Controllers, and Kubernetes services. Additionally if `-f` flag is provided when running `undeploy.sh`, it will also undeploy the shared Governance and User DB pods, Replication Controllers, and Services.
      **`./undeploy.sh -f`**

> For more detailed instructions on deploying a particular WSO2 product on Kubernetes, refer to the README file in the relevant product folder.

# Documentation
* [WSO2 Kubernetes Artifacts Wiki](https://docs.wso2.com/display/KA100/WSO2+Kubernetes+Artifacts)
