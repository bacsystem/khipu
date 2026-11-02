package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.AvisarRequest;
import pe.factura.adapters.rest.dto.AvisosResponse;
import pe.factura.application.port.in.AvisarAlClienteUseCase;
import pe.factura.application.port.in.ConsultarAvisosUseCase;

import java.util.List;
import java.util.UUID;

/**
 * Los avisos a los clientes del backoffice (#197): las empresas con el certificado en riesgo, las que SUNAT rechaza por sus credenciales SOL, y el botón que les manda el aviso por
 * correo. Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma")
public class AdminAvisosController {
    private final ConsultarAvisosUseCase consultar;
    private final AvisarAlClienteUseCase avisar;
    private final String portalUrl;

    public AdminAvisosController(ConsultarAvisosUseCase consultar, AvisarAlClienteUseCase avisar, @Value("${app.portal-url}") String portalUrl) {
        this.consultar = consultar;
        this.avisar = avisar;
        this.portalUrl = portalUrl;
    }

    @GetMapping("/avisos/certificados")
    @Operation(summary = "Empresas con el certificado por vencer o vencido", description = """
            Las empresas cuyo certificado digital vence en **menos de 30 días** o ya venció (la que vence antes, o venció hace más, primero), paginadas; el total va en la cabecera
            `X-Total-Count`. Cada una dice el motivo (`CERTIFICADO_POR_VENCER` o `CERTIFICADO_VENCIDO`), los días que le quedan, a qué cuenta se le avisaría, cuándo se le avisó por
            última vez **por ese motivo** y si se puede avisar ahora (`puede_avisar`: hay a quién escribirle y no se avisó lo mismo en la última semana). No incluye las cuentas dadas
            de baja. Solo lectura.""")
    public ResponseEntity<ApiResponse<List<AvisosResponse.Certificado>>> certificados(
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        var p = consultar.certificados(Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)));
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(p.total())).body(ApiResponse.ok(p.filas().stream().map(AvisosResponse.Certificado::de).toList()));
    }

    @GetMapping("/avisos/credenciales-sol")
    @Operation(summary = "Empresas cuyas credenciales SOL SUNAT no acepta", description = """
            Las empresas con comprobantes **atascados** en error de envío porque SUNAT rechazó su usuario o clave SOL (códigos 0102–0106 y 0111, o un 401 de la autenticación), de la que
            más tiene atascados a la que menos, paginadas; el total va en `X-Total-Count`. **Se deduce de los envíos que fallan ahora**: una empresa que no está emitiendo no aparece, y una
            que ya reenvió con éxito sale sola. Cada una dice cuántos comprobantes tiene así, cuándo falló el último y qué dijo SUNAT. Solo lectura.""")
    public ResponseEntity<ApiResponse<List<AvisosResponse.Sol>>> credencialesSol(
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        var p = consultar.credencialesSol(Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)));
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(p.total())).body(ApiResponse.ok(p.filas().stream().map(AvisosResponse.Sol::de).toList()));
    }

    @PostMapping("/empresas/{id}/avisos")
    @Operation(summary = "Avisarle al cliente por correo", description = """
            Le manda a la cuenta de la empresa un correo de aviso: `CERTIFICADO` (el certificado está por vencer o vencido) o `CREDENCIALES_SOL` (SUNAT no acepta sus credenciales). Se
            avisa **solo lo que es cierto ahora**: si el certificado está vigente o las credenciales no fallan, `409 AVISO_SIN_MOTIVO`. `409 EMPRESA_SIN_CUENTA` si no hay a quién
            escribirle (una empresa de integración). **El mismo aviso no se repite en una semana**: `409 AVISO_RECIENTE` con la fecha desde la que se puede repetir, aunque dos
            administradores hagan clic a la vez; pasar de «por vencer» a «vencido» sí es un aviso nuevo. `503 CORREO_NO_CONFIGURADO` si el servidor no manda correos y
            `502 CORREO_NO_ENVIADO` si el correo falló: en ese caso no queda registrado y se puede reintentar. Queda en la bitácora, sin el correo del cliente.""")
    public ApiResponse<AvisosResponse.Enviado> avisar(@Parameter(description = "Id de la empresa") @PathVariable UUID id, @RequestBody(required = false) AvisarRequest body, HttpServletRequest req) {
        return ApiResponse.ok(AvisosResponse.Enviado.de(avisar.avisar(AdministradorActual.actor(req), id, body == null ? null : body.tipo(), portalUrl)));
    }
}
