package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarConsumoDeCuentasUseCase.Fila;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** El consumo del mes de las cuentas contra el límite de su plan (#193): el mes que se midió, desde qué porcentaje se alerta y una página de cuentas. */
public record ConsumoDeCuentasResponse(
        @Schema(example = "2026-10", description = "El mes calendario (America/Lima) que se midió") String mes,
        @Schema(example = "80", description = "Desde qué porcentaje del tope una cuenta está en alerta (inclusive)") int umbralDeAlerta,
        @Schema(description = "Una página de cuentas, en el orden pedido") List<CuentaConsumo> cuentas) {

    public record CuentaConsumo(
            UUID cuentaId,
            @Schema(example = "Librería Andina") String nombre,
            @Schema(example = "ana@libreria.pe") String email,
            UUID planId,
            @Schema(example = "Emprende", description = "El plan de hoy de la cuenta; se compara con él aunque el mes sea pasado") String plan,
            @Schema(example = "240", description = "Comprobantes aceptados por SUNAT en el mes") long documentos,
            @Schema(example = "300", description = "El tope de documentos al mes que rige hoy; ausente si el plan no tiene tope") Integer limite,
            @Schema(example = "80", description = "El porcentaje del tope usado, hacia abajo; ausente si el plan no tiene tope") Integer porcentaje,
            @Schema(description = "Ya llegó al umbral de alerta") boolean enAlerta,
            @Schema(example = "EN_GRACIA", description = "`VIGENTE`, `EN_GRACIA` o `VENCIDA`") String estadoDelPlan,
            @Schema(description = "Hasta cuándo está pagado el plan; ausente si no vence") Instant pagadoHasta,
            @Schema(description = "Hasta cuándo se la sirve, con la gracia incluida; ausente si no vence") Instant seSirveHasta) {
        static CuentaConsumo de(Fila f) {
            return new CuentaConsumo(f.cuentaId(), f.nombre(), f.email(), f.planId(), f.planNombre(), f.documentos(), f.limite().maximo(), f.porcentaje(), f.enAlerta(), f.estado().name(),
                    f.venceEn(), f.hastaCuandoCubre());
        }
    }

    public static ConsumoDeCuentasResponse de(YearMonth mes, int umbralDeAlerta, List<Fila> filas) {
        return new ConsumoDeCuentasResponse(mes.toString(), umbralDeAlerta, filas.stream().map(CuentaConsumo::de).toList());
    }
}
