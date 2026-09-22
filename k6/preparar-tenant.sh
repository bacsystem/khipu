#!/usr/bin/env bash
# Crea una cuenta + empresa + certificado autofirmado + serie F001 + API key
# dedicados a las pruebas de carga, para no mezclar datos de prueba con
# tenants reales. Imprime la API key al final (úsala como $API_KEY con los
# scripts de k6).
#
# Uso: API_BASE_URL=http://localhost:8080 ./k6/preparar-tenant.sh
set -euo pipefail

BASE_URL="${API_BASE_URL:-http://localhost:8080}"
SUFIJO="$(date +%s)"
EMAIL="carga-k6-${SUFIJO}@example.com"
PASSWORD="Passw0rd1"
# RUC con dígito verificador válido (módulo 11, pesos 5-4-3-2-7-6-5-4-3-2): la API lo comprueba al crear la empresa.
BASE="20$(printf '%08d' $(( (RANDOM * 46341 + RANDOM) % 100000000 )))"
RUC="$(python3 -c "
b='$BASE'; w=[5,4,3,2,7,6,5,4,3,2]; s=sum(int(c)*x for c,x in zip(b,w)); r=11-s%11; d={10:0,11:1}.get(r,r); print(b+str(d))")"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

echo "Registrando cuenta ${EMAIL}..." >&2
REGISTRO=$(curl -sf "$BASE_URL/v1/auth/registro" -X POST -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Carga k6\",\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")
ACCESS=$(echo "$REGISTRO" | node -pe 'JSON.parse(require("fs").readFileSync(0)).datos.access')

echo "Creando empresa RUC ${RUC}..." >&2
EMPRESA=$(curl -sf "$BASE_URL/v1/empresas" -X POST -H "Authorization: Bearer ${ACCESS}" -H "Content-Type: application/json" \
  -d "{\"ruc\":\"${RUC}\",\"razon_social\":\"Carga k6 SAC\",\"entorno\":\"BETA\"}")
EMPRESA_ID=$(echo "$EMPRESA" | node -pe 'JSON.parse(require("fs").readFileSync(0)).datos.id')

echo "Generando certificado autofirmado (OU=${RUC})..." >&2
openssl req -x509 -newkey rsa:2048 -keyout "$TMPDIR/key.pem" -out "$TMPDIR/cert.pem" -days 3650 -nodes \
  -subj "/CN=k6/OU=${RUC}" >/dev/null 2>&1
openssl pkcs12 -export -out "$TMPDIR/cert.p12" -inkey "$TMPDIR/key.pem" -in "$TMPDIR/cert.pem" -passout pass:clave123 >/dev/null 2>&1

curl -sf "$BASE_URL/v1/empresa/certificado" -X POST -H "Authorization: Bearer ${ACCESS}" -H "X-Empresa: ${EMPRESA_ID}" \
  -F "archivo=@${TMPDIR}/cert.p12" -F "clave=clave123" >/dev/null

echo "Creando serie F001..." >&2
curl -sf "$BASE_URL/v1/series" -X POST -H "Authorization: Bearer ${ACCESS}" -H "X-Empresa: ${EMPRESA_ID}" -H "Content-Type: application/json" \
  -d '{"tipo":"01","serie":"F001"}' >/dev/null

echo "Creando API key..." >&2
API_KEY=$(curl -sf "$BASE_URL/v1/empresa/api-keys" -X POST -H "Authorization: Bearer ${ACCESS}" -H "X-Empresa: ${EMPRESA_ID}" \
  | node -pe 'JSON.parse(require("fs").readFileSync(0)).datos.api_key')

echo "" >&2
echo "Listo. Tenant: ${EMPRESA_ID} (RUC ${RUC})" >&2
echo "$API_KEY"
