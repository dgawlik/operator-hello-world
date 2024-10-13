kubectl delete secret regcred

kubectl create secret docker-registry regcred \
  --docker-server=ghcr.io \
  --docker-username=dgawlik \
  --docker-password=$GITHUB_TOKEN