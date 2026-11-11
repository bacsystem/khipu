package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ConsultarMiCuentaUseCase.MiCuenta;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.PeriodoDelTope;

/**
 * Lo que el cliente ve de su cuenta (C1, C7): su nombre, su plan con su estado de pago, lo que consumió contra su tope (del mes, o en total si el plan lo tiene así,
 * #613) y sus pruebas en Beta del mes contra las de su plan (#613).
 */
public record MiCuentaResponse(
        @Schema(example = "Ferretería Torres") String nombre,
        @Schema(description = "El plan de hoy, su estado de pago y una bajada programada, si la hay (la misma forma que ve el backoffice)") PlanDeCuentaResponse plan,
        ConsumoDelMesResponse consumo,
        @Schema(description = "Las pruebas en Beta de este mes, de todas las empresas de la cuenta, contra el tope de pruebas del plan") PruebasDelMesResponse pruebas,
        @Schema(description = "Lo que la cuenta ocupa de los límites de su plan que no son documentos: API keys activas, usuarios (con las invitaciones pendientes) y empresas") UsoDeRecursosResponse uso) {

    public static MiCuentaResponse de(MiCuenta m) {
        Limites limites = m.plan().plan().limites();
        Limite tope = limites.documentosAlMes();
        PeriodoDelTope periodo = limites.periodoDeDocumentos();
        long documentos = periodo == PeriodoDelTope.TOTAL ? m.documentosEnTotal() : m.consumo().documentos();
        Limite pruebas = limites.pruebasBetaAlMes();
        return new MiCuentaResponse(m.nombre(), PlanDeCuentaResponse.de(m.plan()),
                new ConsumoDelMesResponse(m.consumo().mes().toString(), documentos, tope.ilimitado() ? null : tope.maximo(), periodo),
                new PruebasDelMesResponse(m.consumo().mes().toString(), m.pruebasDelMes(), pruebas.ilimitado() ? null : pruebas.maximo()),
                new UsoDeRecursosResponse(m.uso().apiKeys(), m.uso().usuarios(), m.uso().rucs()));
    }

    public record UsoDeRecursosResponse(
            @Schema(example = "1", description = "API keys activas de todas las empresas de la cuenta") long apiKeys,
            @Schema(example = "2", description = "Usuarios activos más invitaciones pendientes que no vencieron") long usuarios,
            @Schema(example = "1", description = "Empresas (RUC) de la cuenta") long rucs) {}

    public record ConsumoDelMesResponse(
            @Schema(example = "2026-10", description = "El mes calendario en curso (America/Lima)") String mes,
            @Schema(example = "12", description = "Lo que consumieron todas las empresas de la cuenta en Producción: lo que SUNAT aceptó, aunque después se anule. Del mes, o "
                    + "de toda la vida de la cuenta si `periodo` es `TOTAL`") long documentos,
            @Schema(example = "300", description = "El tope de documentos del plan de hoy; ausente si es ilimitado") Integer maximo,
            @Schema(example = "MENSUAL", description = "`MENSUAL`: el tope se renueva el día 1. `TOTAL`: es de toda la vida de la cuenta y no se renueva") PeriodoDelTope periodo) {}

    public record PruebasDelMesResponse(
            @Schema(example = "2026-10", description = "El mes calendario en curso (America/Lima)") String mes,
            @Schema(example = "40", description = "Pruebas que SUNAT Beta aceptó este mes, de todas las empresas de la cuenta") long usadas,
            @Schema(example = "3000", description = "El tope de pruebas del mes del plan de hoy (las de cada RUC por sus RUC); ausente si es ilimitado") Integer maximo) {}
}
