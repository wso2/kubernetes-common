#!/bin/bash
# ------------------------------------------------------------------------
#
# Copyright 2015 WSO2, Inc. (http://wso2.com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# ------------------------------------------------------------------------


prgdir=$(dirname "$0")
script_path=$(cd "$prgdir"; pwd)

kubectl get rc | grep mysql-govdb > /dev/null 2>&1

if [ $? -ne 0 ]; then
  echo "Deploying MySQL Governance DB Service..."
  kubectl create -f "${script_path}/mysql-govdb-service.yaml"

  echo "Deploying MySQL Governance DB Replication Controller..."
  kubectl create -f "${script_path}/mysql-govdb-controller.yaml"
else
  echo "MySQL Governance DB is already deployed."
fi

kubectl get rc | grep mysql-userdb > /dev/null 2>&1

if [ $? -ne 0 ]; then
  echo "Deploying MySQL User DB Service..."
  kubectl create -f "${script_path}/mysql-userdb-service.yaml"

  echo "Deploying MySQL User DB Replication Controller..."
  kubectl create -f "${script_path}/mysql-userdb-controller.yaml"
else
  echo "MySQL User DB is already deployed"
fi
