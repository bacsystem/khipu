package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.out.RechazoDeSolRepository;
import pe.factura.domain.tenant.Tenant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmpresaResponse(
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID id,
        @Schema(example = "20123456786") String ruc,
        @Schema(example = "Comercial Andina SAC") String razonSocial,
        @Schema(example = "BETA") String entorno,
        @Schema(example = "true") boolean tieneCredencialesSol,
        @Schema(example = "true", description = "Si hay un certificado digital cargado. Aparte de la vigencia: un certificado puede no tener fecha") boolean tieneCertificado,
        @Schema(example = "2027-12-31") LocalDate certificadoVigenciaHasta,
        @Schema(description = "Domicilio fiscal, o `null` si aún no se configuró") DomicilioResponse domicilio,
        @Schema(example = "00-000-123456", description = "Cuenta de detracciones por defecto, o `null`") String cuentaDetracciones,
        @Schema(example = "Andina Store", description = "Nombre comercial que va en el XML (`cac:PartyName`), o `null`") String nombreComercial,
        @Schema(example = "false", description = "Inscrita en el Padrón de Tasa Especial del IGV (restaurantes y hoteles): emite con la tasa reducida") boolean padronTasaEspecialIgv,
        @Schema(description = "#107: si SUNAT rechazó las credenciales SOL (usuario o clave incorrectos…), cuándo y por qué; ausente si no. Mientras esté, los envíos de la empresa esperan a que se corrijan")
        CredencialesSolRechazadas credencialesSolRechazadas) {

    public record CredencialesSolRechazadas(@Schema(example = "2026-10-09T15:00:00Z") Instant desde, @Schema(example = "0102 - Usuario o contrasena incorrectos") String motivo) {}

    public static EmpresaResponse de(Tenant t, RechazoDeSolRepository.Rechazo rechazo) {
        return new EmpresaResponse(t.id(), t.ruc(), t.razonSocial(), t.entorno().name(), t.sol() != null, t.certificado() != null,
                t.certificado() == null ? null : t.certificado().vigenciaHasta(), DomicilioResponse.de(t.domicilio()), t.cuentaDetracciones(), t.nombreComercial(),
                t.padronTasaEspecialIgv(), rechazo == null ? null : new CredencialesSolRechazadas(rechazo.en(), rechazo.motivo()));
    }

    public static EmpresaResponse de(Tenant t) {
        return de(t, null);
    }
}
