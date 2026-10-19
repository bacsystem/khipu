package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.factura.domain.documento.ClienteGuardado;

import java.time.Instant;
import java.util.UUID;

/** Pedidos y respuestas de los clientes guardados (#319). */
public final class ClienteDtos {
    private ClienteDtos() {}

    public record Crear(
            @NotBlank @Schema(example = "6", description = "Tipo de documento del catálogo 06: 1 DNI, 4 carné de extranjería, 6 RUC, 7 pasaporte…") String tipoDoc,
            @NotBlank @Size(max = 20) @Schema(example = "20601234565") String numDoc,
            @NotBlank @Size(max = 1500) @Schema(example = "COMERCIAL ANDINA S.A.C.") String razonSocial,
            @Size(max = 300) @Schema(example = "Av. Javier Prado Este 123, San Isidro, Lima") String direccion,
            @Size(max = 2) @Schema(example = "PE", description = "País ISO 3166-1 (catálogo 04); opcional") String pais,
            @Size(max = 150) @Schema(example = "facturas@andina.pe") String email) {
        public ClienteGuardado.Datos datos() { return new ClienteGuardado.Datos(tipoDoc, numDoc, razonSocial, direccion, pais, email); }
    }

    /** Lo mismo sin el documento, que en el guardado por documento va en la ruta. */
    public record Guardar(
            @NotBlank @Size(max = 1500) String razonSocial,
            @Size(max = 300) String direccion,
            @Size(max = 2) String pais,
            @Size(max = 150) String email) {
        public ClienteGuardado.Datos datos(String tipoDoc, String numDoc) { return new ClienteGuardado.Datos(tipoDoc, numDoc, razonSocial, direccion, pais, email); }
    }

    public record Cliente(UUID id, String tipoDoc, String numDoc, String razonSocial, String direccion, String pais, String email, Instant creadoEn, Instant actualizadoEn) {
        public static Cliente de(ClienteGuardado c) {
            return new Cliente(c.id(), c.tipoDoc(), c.numDoc(), c.razonSocial(), c.direccion(), c.pais(), c.email(), c.creadoEn(), c.actualizadoEn());
        }
    }
}
