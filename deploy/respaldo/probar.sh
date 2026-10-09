#!/bin/sh
# Prueba de punta a punta del respaldo (S11), en contenedores propios y temporales: no toca el Postgres de desarrollo ni el despliegue
# de develop. Arma una base con las migraciones reales y datos de ejemplo, la respalda a un MinIO, restaura en una base vacía, compara
# tabla por tabla y prueba que una clave equivocada no descifra y que una base con datos no se pisa sin CONFIRMAR=si.
#
#   sh deploy/respaldo/probar.sh       (desde la raíz del repo; `make probar-respaldo`)
set -eu

red="khipu-prueba-respaldo"
imagen="khipu-respaldo:prueba"
clave="clave-de-prueba-de-respaldo-larga"
raiz="$(cd "$(dirname "$0")/../.." && pwd)"

limpiar() {
  # Todo lo que esté en la red de la prueba, también los `docker run --rm` de una corrida interrumpida.
  docker ps -aq --filter "network=$red" | xargs -r docker rm -f >/dev/null 2>&1 || true
  docker rm -f khipu-pr-origen khipu-pr-destino khipu-pr-minio >/dev/null 2>&1 || true
  docker network rm "$red" >/dev/null 2>&1 || true
}
trap limpiar EXIT
limpiar

docker network create "$red" >/dev/null
docker build -q -t "$imagen" "$raiz/deploy/respaldo" >/dev/null
for nombre in origen destino; do
  docker run -d --name "khipu-pr-$nombre" --network "$red" -e POSTGRES_USER=khipu -e POSTGRES_PASSWORD=khipu -e POSTGRES_DB=khipu postgres:16-alpine >/dev/null
done
# `--user 0:0` como en docker-compose.yml: la imagen corre como 65532 y no puede escribir en /data.
docker run -d --name khipu-pr-minio --network "$red" --user 0:0 -e MINIO_ROOT_USER=khipu -e MINIO_ROOT_PASSWORD=khipu-minio \
  cgr.dev/chainguard/minio:latest server /data >/dev/null

# Toda espera tiene tope: un contenedor que no arranca no puede colgar la prueba.
esperar() { n=0; until "$@" >/dev/null 2>&1; do n=$((n + 1)); [ $n -lt 180 ] || { echo "FALLO: no respondió a tiempo: $*"; docker ps -a --filter "network=$red"; exit 1; }; sleep 1; done; }
esperar_pg() { esperar docker exec "$1" pg_isready -U khipu -d khipu; sleep 2; }
esperar_pg khipu-pr-origen
esperar_pg khipu-pr-destino

# Las migraciones reales de Flyway, en orden, y unas filas: lo que importa es que el esquema entero y los datos vuelvan iguales.
for m in $(ls "$raiz"/adapters/out-persistence/src/main/resources/db/migration/V*.sql | sort -V); do
  docker exec -i khipu-pr-origen psql -q -v ON_ERROR_STOP=1 -U khipu -d khipu < "$m" >/dev/null
done
docker exec -i khipu-pr-origen psql -q -v ON_ERROR_STOP=1 -U khipu -d khipu >/dev/null <<'SQL'
INSERT INTO cuenta (id, nombre, email) VALUES ('00000000-0000-4000-8000-000000000001', 'Ferretería Ñandú', 'ana@prueba.pe');
SQL

env_s3="-e BACKUP_PASSPHRASE=$clave -e BACKUP_S3_BUCKET=respaldos -e BACKUP_S3_ENDPOINT=http://khipu-pr-minio:9000
        -e AWS_ACCESS_KEY_ID=khipu -e AWS_SECRET_ACCESS_KEY=khipu-minio -e AWS_DEFAULT_REGION=us-east-1"
correr() { docker run --rm --network "$red" $env_s3 "$@"; }

esperar correr "$imagen" aws --endpoint-url http://khipu-pr-minio:9000 s3 mb s3://respaldos

echo "1) respaldar"
correr -e DATABASE_URL=postgres://khipu:khipu@khipu-pr-origen:5432/khipu "$imagen" respaldar.sh

echo "2) una clave equivocada no descifra"
if docker run --rm --network "$red" $env_s3 -e BACKUP_PASSPHRASE=otra-clave-equivocada-larga \
     -e RESTAURAR_EN=postgres://khipu:khipu@khipu-pr-destino:5432/khipu "$imagen" restaurar.sh --ultimo >/dev/null 2>&1; then
  echo "FALLO: restauró con una clave equivocada"; exit 1
fi

echo "3) restaurar el último en la base vacía"
correr -e RESTAURAR_EN=postgres://khipu:khipu@khipu-pr-destino:5432/khipu "$imagen" restaurar.sh --ultimo

echo "4) comparar tabla por tabla"
conteos() {
  docker exec "$1" psql -At -U khipu -d khipu -c "
    SELECT string_agg(t.table_name || '=' || (xpath('/row/n/text()', query_to_xml('SELECT count(*) AS n FROM public.' || quote_ident(t.table_name), false, true, '')))[1]::text, ',' ORDER BY t.table_name)
    FROM information_schema.tables t WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'"
}
a="$(conteos khipu-pr-origen)"; b="$(conteos khipu-pr-destino)"
[ "$a" = "$b" ] || { echo "FALLO: las tablas no coinciden"; echo "origen:  $a"; echo "destino: $b"; exit 1; }
nombre="$(docker exec khipu-pr-destino psql -At -U khipu -d khipu -c "SELECT nombre FROM cuenta")"
[ "$nombre" = "Ferretería Ñandú" ] || { echo "FALLO: el texto no volvió igual: $nombre"; exit 1; }
echo "   $(echo "$a" | tr ',' '\n' | wc -l | tr -d ' ') tablas iguales, con tildes y ñ intactas"

echo "5) no pisa una base con datos sin CONFIRMAR=si"
if correr -e RESTAURAR_EN=postgres://khipu:khipu@khipu-pr-destino:5432/khipu "$imagen" restaurar.sh --ultimo >/dev/null 2>&1; then
  echo "FALLO: restauró encima de una base con datos sin confirmar"; exit 1
fi
correr -e RESTAURAR_EN=postgres://khipu:khipu@khipu-pr-destino:5432/khipu -e CONFIRMAR=si "$imagen" restaurar.sh --ultimo >/dev/null

echo "OK: respaldo y restauración probados"
