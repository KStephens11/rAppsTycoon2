#!/bin/bash
# Generates k8s/secret.yaml from user input. Run once before deploying to Kubernetes.

read -rsp "MYSQL_ROOT_PASSWORD: " ROOT_PASS && echo
read -rsp "MYSQL_PASSWORD: " PASS && echo
read -rsp "INTERNAL_API_KEY: " API_KEY && echo

encode() { python3 -c "import base64, sys; print(base64.b64encode(sys.argv[1].encode()).decode())" "$1"; }

cat > k8s/secret.yaml <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: rapp-secret
type: Opaque
data:
  MYSQL_ROOT_PASSWORD: $(encode "$ROOT_PASS")
  MYSQL_DATABASE: cmFwcHR5Y29vbg==
  MYSQL_USER: cmFwcHR5Y29vbg==
  MYSQL_PASSWORD: $(encode "$PASS")
  INTERNAL_API_KEY: $(encode "$API_KEY")
EOF

echo "k8s/secret.yaml generated."
