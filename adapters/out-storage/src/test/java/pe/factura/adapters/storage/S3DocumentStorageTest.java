package pe.factura.adapters.storage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrato del storage S3 contra MinIO (#38): mismas claves y semántica que el de disco, PutObject con checksum SHA-256. */
@Testcontainers
class S3DocumentStorageTest {
    // Docker Hub retiró minio/minio (2025); la imagen oficial vive en quay.io.
    @Container static final MinIOContainer MINIO = new MinIOContainer(DockerImageName.parse("quay.io/minio/minio:latest").asCompatibleSubstituteFor("minio/minio"));
    static final String BUCKET = "khipu-test";
    static S3DocumentStorage storage;

    @BeforeAll static void bucket() {
        try (S3Client admin = S3Client.builder().endpointOverride(URI.create(MINIO.getS3URL())).region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword()))).build()) {
            admin.createBucket(b -> b.bucket(BUCKET));
        }
        storage = S3DocumentStorage.crear(MINIO.getS3URL(), "us-east-1", MINIO.getUserName(), MINIO.getPassword(), BUCKET, true);
    }

    @Test void guardaLeeYSobrescribe() {
        String key = "t1/2026/09/20100066603-01-F001-1.xml";
        storage.guardar(key, "<x/>".getBytes());
        assertThat(storage.existe(key)).isTrue();
        assertThat(new String(storage.leer(key))).isEqualTo("<x/>");
        storage.guardar(key, "<y/>".getBytes());
        assertThat(new String(storage.leer(key))).isEqualTo("<y/>");
    }

    @Test void existeYBorrarSonIdempotentes() {
        assertThat(storage.existe("t1/2026/09/no-existe.zip")).isFalse();
        storage.guardar("t1/logo-abc.png", new byte[]{1, 2, 3});
        storage.borrar("t1/logo-abc.png");
        assertThat(storage.existe("t1/logo-abc.png")).isFalse();
        storage.borrar("t1/logo-abc.png");
    }

    @Test void leerInexistenteLanza() {
        assertThatThrownBy(() -> storage.leer("t1/2026/09/no-existe.xml")).isInstanceOf(IllegalStateException.class).hasMessageContaining("No existe");
    }

    @Test void rechazaClavesInvalidas() {
        assertThatThrownBy(() -> storage.guardar("../fuera.xml", new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.existe("/absoluta")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new S3DocumentStorage(null, " ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("STORAGE_S3_BUCKET");
    }

    @Test void conservaBinariosGrandes() {
        byte[] pdf = new byte[3 * 1024 * 1024];
        for (int i = 0; i < pdf.length; i++) pdf[i] = (byte) (i * 31);
        storage.guardar("t1/2026/09/20100066603-01-F001-2.pdf", pdf);
        assertThat(storage.leer("t1/2026/09/20100066603-01-F001-2.pdf")).isEqualTo(pdf);
    }
}
