package pe.factura.domain.tenant;
import java.time.LocalDate;
public record CertificadoDigital(byte[] pkcs12, String clave, LocalDate vigenciaHasta) {}
