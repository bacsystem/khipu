# Respaldo de Postgres a S3 (S11): `pg_dump` cifrado y subido a un bucket S3 o compatible (AWS, Cloudflare R2, Backblaze B2, MinIO).
# En Railway corre como un servicio aparte con Cron Schedule (ver deploy/README.md §6). La versión de Postgres es la misma que la de
# la base (16): `pg_dump` no respalda un servidor más nuevo que él.
FROM postgres:16-alpine

# aws-cli habla con cualquier S3 compatible vía `--endpoint-url`; openssl cifra el volcado con BACKUP_PASSPHRASE.
RUN apk add --no-cache aws-cli openssl

COPY respaldar.sh restaurar.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/respaldar.sh /usr/local/bin/restaurar.sh \
 && adduser -D -u 10001 respaldo
USER respaldo
WORKDIR /tmp

ENTRYPOINT []
CMD ["respaldar.sh"]
