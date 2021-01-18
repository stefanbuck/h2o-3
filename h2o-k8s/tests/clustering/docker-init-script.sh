#! /bin/bash -x

pwd
export H2O_BASE=$(pwd)
if [[ $string == *"@"* ]]; then
  echo "H2O base path contains at sign. Unable to create K3S cluster."
  exit 1
fi
cd $H2O_BASE/h2o-k8s/tests/clustering/
k3d --version
k3d delete
k3d create -v "$H2O_BASE":"$H2O_BASE" --registries-file registries.yaml --publish 8080:80 --api-port localhost:6444 --server-arg --tls-san="127.0.0.1" --wait 120
export KUBECONFIG="$(k3d get-kubeconfig --name='k3s-default')"
kubectl cluster-info
sleep 15 # Making sure the default namespace is initialized. The --wait flag does not guarantee this.
kubectl get namespaces
envsubst < testvalues-template.yaml >> testvalues.yaml
helm install -f testvalues.yaml h2o $H2O_BASE/h2o-helm --kubeconfig $KUBECONFIG --dry-run # Shows resulting YAML
helm install -f testvalues.yaml h2o $H2O_BASE/h2o-helm --kubeconfig $KUBECONFIG
helm test h2o
kubectl logs h2o-h2o-3-test-connection
kubectl get ingresses
kubectl describe pods
timeout 120s bash h2o-cluster-check.sh
export CLUSTER_EXIT_CODE=$?
kubectl get pods
kubectl get nodes
helm uninstall h2o 
envsubst < h2o-assisted-template.yaml >> h2o-assisted.yaml
envsubst < h2o-python-clustering-template.yaml >> h2o-python-clustering.yaml
kubectl apply -f h2o-assisted.yaml
kubectl wait --timeout=180s --for=condition=ready --selector app=h2o-assisted pods
kubectl apply -f h2o-python-clustering.yaml
kubectl wait --timeout=180s --for=condition=ready --selector app=h2o-assisted-python pods
kubectl get services
kubectl get ingresses
kubectl describe pods
timeout 120s bash h2o-cluster-check.sh
export ASSISTED_EXIT_CODE=$?
kubectl get pods

k3d delete
export EXIT_STATUS=$(if [ "$CLUSTER_EXIT_CODE" -eq 1 ] || [ "$ASSISTED_EXIT_CODE" -eq 1 ]; then echo 1; else echo 0; fi)
exit $EXIT_STATUS
