package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El mismo flujo de {@link FacturaE2ETest} (emisión, descarga de XML y CDR, outbox, auth) con `STORAGE_TYPE=s3` contra MinIO
 * (#38): los tests heredados corren sobre el storage S3, y además se verifica la integridad del periodo por la API de administración.
 */
class FacturaS3E2ETest extends FacturaE2ETest {
    @Container static MinIOContainer minio = new MinIOContainer(DockerImageName.parse("quay.io/minio/minio:latest").asCompatibleSubstituteFor("minio/minio"));

    @DynamicPropertySource static void storage(DynamicPropertyRegistry r) {
        r.add("app.storage.type", () -> "s3");
        r.add("app.storage.s3.bucket", () -> "khipu-e2e");
        r.add("app.storage.s3.endpoint", minio::getS3URL);
        r.add("app.storage.s3.access-key", minio::getUserName);
        r.add("app.storage.s3.secret-key", minio::getPassword);
        r.add("app.storage.s3.path-style", () -> "true");
        try (S3Client admin = S3Client.builder().endpointOverride(URI.create(minio.getS3URL())).region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.getUserName(), minio.getPassword()))).build()) {
            admin.createBucket(b -> b.bucket("khipu-e2e"));
        }
    }

    @Test void laIntegridadDelPeriodoEstaLimpiaTrasEmitir() throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        HttpHeaders admin = new HttpHeaders(); admin.set("X-Platform-Key", "plataforma-test");
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();
        ResponseEntity<Map> r = http.exchange("/v1/admin/integridad?desde=" + hoy + "&hasta=" + hoy, HttpMethod.POST, new HttpEntity<>(admin), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> informe = (Map<?, ?>) r.getBody().get("datos");
        assertThat(((Number) informe.get("verificados")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat((List<?>) informe.get("problemas")).isEmpty();
    }
}
