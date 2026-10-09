#!/bin/sh
# Respaldo de la base (S11): pg_dump en formato custom → cifrado AES-256 (PBKDF2) con BACKUP_PASSPHRASE → bucket S3.
# Termina con código distinto de 0 ante cualquier fallo, para que Railway marque la ejecución del cron como fallida.
#
# Variables:
#   DATABASE_URL            postgres://usuario:clave@host:5432/base (la que inyecta el plugin de Postgres de Railway)
#   BACKUP_PASSPHRASE       obligatoria; sin ella el respaldo no se puede leer. Guardarla FUERA de Railway (gestor de contraseñas):
#                           si se pierde junto con el proyecto, los respaldos no sirven.
#   BACKUP_S3_BUCKET        bucket de destino
#   BACKUP_S3_PREFIJO       opcional, carpeta dentro del bucket (por defecto `postgres`)
#   BACKUP_S3_ENDPOINT      opcional, para S3 compatibles (R2, B2, MinIO)
#   AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_DEFAULT_REGION   credenciales del bucket (región `auto` en R2)
set -eu

falta() { echo "respaldo: falta la variable $1" >&2; exit 2; }
[ -n "${DATABASE_URL:-}" ] || falta DATABASE_URL
[ -n "${BACKUP_PASSPHRASE:-}" ] || falta BACKUP_PASSPHRASE
[ -n "${BACKUP_S3_BUCKET:-}" ] || falta BACKUP_S3_BUCKET
[ ${#BACKUP_PASSPHRASE} -ge 20 ] || { echo "respaldo: BACKUP_PASSPHRASE debe tener al menos 20 caracteres" >&2; exit 2; }

prefijo="${BACKUP_S3_PREFIJO:-postgres}"
marca="$(date -u +%Y%m%dT%H%M%SZ)"
nombre="khipu-${marca}.dump.enc"
destino="s3://${BACKUP_S3_BUCKET}/${prefijo}/${nombre}"
s3() { if [ -n "${BACKUP_S3_ENDPOINT:-}" ]; then aws --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"; else aws "$@"; fi; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# --no-owner/--no-privileges: se restaura en otra base con otro usuario sin pelear por dueños. El formato custom ya va comprimido.
pg_dump --format=custom --no-owner --no-privileges --dbname="$DATABASE_URL" --file="$tmp/khipu.dump"
# Un volcado vacío o truncado no se sube como si fuera un respaldo bueno.
pg_restore --list "$tmp/khipu.dump" > "$tmp/indice"
tablas="$(grep -c ' TABLE DATA ' "$tmp/indice" || true)"
[ "$tablas" -gt 0 ] || { echo "respaldo: el volcado no tiene datos de ninguna tabla" >&2; exit 1; }

openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:BACKUP_PASSPHRASE -in "$tmp/khipu.dump" -out "$tmp/$nombre"
s3 s3 cp --only-show-errors "$tmp/$nombre" "$destino"

# Comprobar que lo que quedó en el bucket es lo que se subió (mismo tamaño); si no, el respaldo no cuenta.
local_bytes="$(wc -c < "$tmp/$nombre" | tr -d ' ')"
remoto_bytes="$(s3 s3api head-object --bucket "$BACKUP_S3_BUCKET" --key "${prefijo}/${nombre}" --query ContentLength --output text)"
[ "$local_bytes" = "$remoto_bytes" ] || { echo "respaldo: el objeto subido mide $remoto_bytes bytes y el local $local_bytes" >&2; exit 1; }

echo "respaldo: ${destino} (${local_bytes} bytes, ${tablas} tablas con datos)"
