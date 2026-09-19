package pe.factura.adapters.storage;

import pe.factura.application.port.out.DocumentStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Storage durable en S3 o compatible —MinIO, Backblaze B2, Cloudflare R2— (`STORAGE_TYPE=s3`, #38): los XML firmados y sus
 * CDR deben conservarse el plazo de prescripción (≥ 5 años). Cada PutObject es atómico (el objeto aparece completo o no
 * aparece) y viaja con checksum SHA-256 que el servidor verifica; el versionado y la retención se configuran en el bucket
 * (ver README §Storage). Las claves son las mismas que en disco (`{tenant}/{yyyy}/{MM}/{nombre}`), así migrar es copiar.
 */
public class S3DocumentStorage implements DocumentStorage {
    private final S3Client s3;
    private final String bucket;

    public S3DocumentStorage(S3Client s3, String bucket) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("STORAGE_S3_BUCKET es obligatorio con STORAGE_TYPE=s3");
        this.s3 = s3;
        this.bucket = bucket;
    }

    /**
     * Cliente para AWS (sin {@code endpoint}, credenciales de la cadena por defecto si {@code accessKey} va vacío) o para un
     * servicio compatible ({@code endpoint} + credenciales estáticas + path-style, que MinIO exige).
     */
    public static S3DocumentStorage crear(String endpoint, String region, String accessKey, String secretKey, String bucket, boolean pathStyle) {
        var b = S3Client.builder()
                .region(Region.of(region == null || region.isBlank() ? "us-east-1" : region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        if (endpoint != null && !endpoint.isBlank()) b.endpointOverride(URI.create(endpoint));
        b.credentialsProvider(accessKey == null || accessKey.isBlank()
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        return new S3DocumentStorage(b.build(), bucket);
    }

    @Override public void guardar(String key, byte[] contenido) {
        validar(key);
        // Checksum calculado aquí y enviado como cabecera: el servidor rechaza (400) cualquier objeto que llegue alterado o truncado.
        // (Con el algoritmo delegado al SDK, el cliente HTTP por URLConnection lo manda como trailer chunked, que MinIO no acepta.)
        String sha256 = Base64.getEncoder().encodeToString(sha256(contenido));
        try {
            s3.putObject(r -> r.bucket(bucket).key(key).checksumSHA256(sha256), RequestBody.fromBytes(contenido));
        } catch (S3Exception e) { throw new IllegalStateException("No se pudo guardar " + key + " en s3://" + bucket, e); }
    }

    private static byte[] sha256(byte[] contenido) {
        try { return MessageDigest.getInstance("SHA-256").digest(contenido); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @Override public byte[] leer(String key) {
        validar(key);
        try {
            ResponseBytes<GetObjectResponse> r = s3.getObjectAsBytes(b -> b.bucket(bucket).key(key));
            return r.asByteArray();
        } catch (NoSuchKeyException e) { throw new IllegalStateException("No existe " + key);
        } catch (S3Exception e) { throw new IllegalStateException("No se pudo leer " + key + " de s3://" + bucket, e); }
    }

    @Override public boolean existe(String key) {
        validar(key);
        try {
            s3.headObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) { return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw new IllegalStateException("No se pudo consultar " + key + " en s3://" + bucket, e);
        }
    }

    @Override public void borrar(String key) {
        validar(key);
        try { s3.deleteObject(b -> b.bucket(bucket).key(key)); }   // idempotente: S3 responde 204 aunque no exista
        catch (S3Exception e) { throw new IllegalStateException("No se pudo borrar " + key + " de s3://" + bucket, e); }
    }

    private static void validar(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) throw new IllegalArgumentException("Clave inválida: " + key);
    }
}
