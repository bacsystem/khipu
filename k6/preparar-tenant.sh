#!/usr/bin/env bash
# Crea una empresa aislada para las pruebas de carga: alta con la clave de la plataforma, credenciales SOL de prueba, certificado autofirmado
# (con el RUC en el `OU`, que es lo único que valida el backend), las series que pidas y una API key. Imprime la API key al final (úsala como
# $API_KEY con los scripts de k6); los pasos intermedios van a stderr.
#
# Usa la clave de la plataforma y no el registro de una cuenta: el registro está cerrado por defecto (REGISTRO_ABIERTO) y pide teléfono y correo
# verificado, y una prueba de carga no tiene por qué pasar por el portal.
#
# Uso:
#   API_BASE_URL=http://localhost:8001 PLATFORM_ADMIN_KEY=... ./k6/preparar-tenant.sh
#   SERIES="01:F001 03:B001" ...    # tipo:serie separados por espacio; por defecto solo 01:F001
set -euo pipefail

BASE_URL="${API_BASE_URL:-http://localhost:8001}"
: "${PLATFORM_ADMIN_KEY:?Define PLATFORM_ADMIN_KEY (la clave de la plataforma del backend contra el que vas a correr la carga)}"
SERIES="${SERIES:-01:F001}"
# RUC con dígito verificador válido (módulo 11, pesos 5-4-3-2-7-6-5-4-3-2): la API lo comprueba al crear la empresa.
BASE="20$(printf '%08d' $(( (RANDOM * 46341 + RANDOM) % 100000000 )))"
RUC="$(python3 -c "
b='$BASE'; w=[5,4,3,2,7,6,5,4,3,2]; s=sum(int(c)*x for c,x in zip(b,w)); r=11-s%11; d={10:0,11:1}.get(r,r); print(b+str(d))")"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

json() { node -pe "JSON.parse(require('fs').readFileSync(0)).datos.$1"; }

echo "Creando la empresa RUC ${RUC}..." >&2
ALTA=$(curl -sf "$BASE_URL/v1/admin/tenants" -X POST -H "X-Platform-Key: ${PLATFORM_ADMIN_KEY}" -H "Content-Type: application/json" \
  -d "{\"ruc\":\"${RUC}\",\"razon_social\":\"Carga k6 SAC\",\"entorno\":\"BETA\"}")
API_KEY=$(echo "$ALTA" | json api_key)

echo "Guardando credenciales SOL de prueba..." >&2
curl -sf "$BASE_URL/v1/empresa/credenciales-sol" -X PUT -H "X-Api-Key: ${API_KEY}" -H "Content-Type: application/json" \
  -d '{"usuario":"MODDATOS","clave":"moddatos"}' >/dev/null

echo "Generando certificado autofirmado (OU=${RUC})..." >&2
openssl req -x509 -newkey rsa:2048 -keyout "$TMPDIR/key.pem" -out "$TMPDIR/cert.pem" -days 3650 -nodes \
  -subj "/CN=k6/OU=${RUC}" >/dev/null 2>&1
openssl pkcs12 -export -out "$TMPDIR/cert.p12" -inkey "$TMPDIR/key.pem" -in "$TMPDIR/cert.pem" -passout pass:clave123 >/dev/null 2>&1
curl -sf "$BASE_URL/v1/empresa/certificado" -X POST -H "X-Api-Key: ${API_KEY}" -F "archivo=@${TMPDIR}/cert.p12" -F "clave=clave123" >/dev/null

for par in $SERIES; do
  TIPO="${par%%:*}"; SERIE="${par##*:}"
  echo "Creando serie ${SERIE} (tipo ${TIPO})..." >&2
  curl -sf "$BASE_URL/v1/series" -X POST -H "X-Api-Key: ${API_KEY}" -H "Content-Type: application/json" \
    -d "{\"tipo\":\"${TIPO}\",\"serie\":\"${SERIE}\"}" >/dev/null
done

echo "" >&2
echo "Listo. Empresa RUC ${RUC}; series: ${SERIES}" >&2
echo "$API_KEY"
