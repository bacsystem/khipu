package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import pe.factura.domain.tenant.Domicilio;

/** Domicilio fiscal, cuenta de detracciones, nombre comercial y tasa especial del IGV de la empresa: cada `PUT` reemplaza los cuatro (`null` borra). */
public record DatosFiscalesRequest(
        @Valid @Schema(description = "Domicilio fiscal tal como figura en la ficha RUC; va en el XML como `RegistrationAddress` del emisor. `null` lo borra (el XML solo llevará el establecimiento 0000).") DomicilioDto domicilio,
        @Schema(example = "00-000-123456", description = "Cuenta de detracciones en el Banco de la Nación; se usa cuando una factura sujeta a detracción no indica `cuenta_banco_nacion`. `null` la borra") String cuentaDetracciones,
        @Schema(example = "Andina Store", description = "Nombre comercial (hasta 1500 caracteres, sin saltos de línea; regla 4092); va en el XML como `cac:PartyName` del emisor. `null` lo borra") String nombreComercial,
        @Schema(example = "false", description = "`true` si la empresa está inscrita en el Padrón de Tasa Especial del IGV (MYPE de restaurantes y hoteles, Ley 31556): sus facturas, boletas y notas gravadas llevan la tasa reducida vigente (10.5 % desde el 2026-02-13) en vez del 18 %. SUNAT observa (4439) al que la declara sin estar en el padrón. Por defecto `false`") Boolean padronTasaEspecialIgv) {

    public boolean tasaEspecial() { return Boolean.TRUE.equals(padronTasaEspecialIgv); }

    public record DomicilioDto(
            @NotBlank @Pattern(regexp = "\\d{6}", message = "ubigeo de 6 dígitos (catálogo 13)") @Schema(example = "150122", description = "Ubigeo INEI del distrito, catálogo 13 (`GET /v1/catalogos/13`); khipu completa distrito, provincia y departamento a partir de él") String ubigeo,
            @NotBlank @Schema(example = "Av. Javier Prado Este 123 Of. 501", description = "Dirección completa en una sola línea, de 3 a 200 caracteres (regla 4094)") String direccion,
            @Schema(example = "Urb. Jardín", description = "Urbanización (opcional, hasta 25 caracteres)") String urbanizacion,
            @Schema(example = "MIRAFLORES", description = "Distrito (opcional: por defecto el del ubigeo)") String distrito,
            @Schema(example = "LIMA", description = "Provincia (opcional: por defecto la del ubigeo)") String provincia,
            @Schema(example = "LIMA", description = "Departamento (opcional: por defecto el del ubigeo)") String departamento,
            @Schema(example = "0000", description = "Código del establecimiento anexo declarado en el RUC; `0000` (por defecto) es el domicilio fiscal. Un código distinto debe existir en la ficha RUC (regla 3030)") String codigoEstablecimiento) {
        public Domicilio aDominio() { return new Domicilio(ubigeo, direccion, urbanizacion, distrito, provincia, departamento, codigoEstablecimiento); }
    }
}
