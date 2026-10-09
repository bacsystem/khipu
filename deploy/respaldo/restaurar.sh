#!/bin/sh
# Restauración de un respaldo (S11): baja el objeto del bucket, lo descifra con BACKUP_PASSPHRASE y lo restaura en RESTAURAR_EN.
#
#   restaurar.sh <clave-del-objeto>     p. ej. postgres/khipu-20261009T080000Z.dump.enc
#   restaurar.sh --ultimo                el más reciente del prefijo
#
# Variables: las mismas de respaldar.sh para el bucket y la clave, más
#   RESTAURAR_EN    postgres://… de la base DESTINO. Obligatoria y aparte de DATABASE_URL a propósito: restaurar encima de la base de
#                   producción por un descuido es justo lo que no tiene que pasar.
#   CONFIRMAR=si    obligatoria si la base destino ya tiene tablas: se borran y se reemplazan por las del respaldo.
set -eu

falta() { echo "restaurar: falta la variable $1" >&2; exit 2; }
[ -n "${RESTAURAR_EN:-}" ] || falta RESTAURAR_EN
[ -n "${BACKUP_PASSPHRASE:-}" ] || falta BACKUP_PASSPHRASE
[ -n "${BACKUP_S3_BUCKET:-}" ] || falta BACKUP_S3_BUCKET
[ $# -eq 1 ] || { echo "uso: restaurar.sh <clave-del-objeto> | --ultimo" >&2; exit 2; }

prefijo="${BACKUP_S3_PREFIJO:-postgres}"
s3() { if [ -n "${BACKUP_S3_ENDPOINT:-}" ]; then aws --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"; else aws "$@"; fi; }

clave="$1"
if [ "$clave" = "--ultimo" ]; then
  # Los nombres llevan la fecha en UTC y orden lexicográfico = orden cronológico.
  clave="$(s3 s3api list-objects-v2 --bucket "$BACKUP_S3_BUCKET" --prefix "${prefijo}/khipu-" --query 'Contents[].Key' --output text | tr '\t' '\n' | sort | tail -n 1)"
  [ -n "$clave" ] && [ "$clave" != "None" ] || { echo "restaurar: no hay respaldos en s3://${BACKUP_S3_BUCKET}/${prefijo}/" >&2; exit 1; }
fi

tablas_destino="$(psql "$RESTAURAR_EN" -At -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")"
if [ "$tablas_destino" -gt 0 ] && [ "${CONFIRMAR:-}" != "si" ]; then
  echo "restaurar: la base destino tiene $tablas_destino tablas; se borrarían. Repetir con CONFIRMAR=si si es lo que se quiere." >&2
  exit 3
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

s3 s3 cp --only-show-errors "s3://${BACKUP_S3_BUCKET}/${clave}" "$tmp/respaldo.enc"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$tmp/respaldo.enc" -out "$tmp/khipu.dump" \
  || { echo "restaurar: no se pudo descifrar (¿BACKUP_PASSPHRASE equivocada?)" >&2; exit 1; }
# --exit-on-error: una restauración a medias no se da por buena.
pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error --dbname="$RESTAURAR_EN" "$tmp/khipu.dump"

echo "restaurar: ${clave} restaurado"
