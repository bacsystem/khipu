package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.MonitorDeEmisionResponse;
import pe.factura.application.port.in.MonitorearEmisionUseCase;

/** El monitor global de emisión (#195): de todas las empresas a la vez, solo lectura, solo para la plataforma. */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma")
@RequiredArgsConstructor
public class AdminMonitorController {
    private final MonitorearEmisionUseCase monitor;

    @GetMapping("/monitor")
    @Operation(summary = "Monitor global de emisión", description = """
            Cómo va la emisión de **todas** las empresas: los comprobantes de las últimas 24 horas por hora (aceptados, rechazados, con error de envío, en camino), el total
            del día de Lima con su tasa de rechazo, la cola del outbox (pendientes, vencidos y una **alerta** cuando hay envíos vencidos hace más de 5 minutos, señal de que
            el trabajo que la vacía no corre) y si los servicios de SUNAT contestan. El estado de SUNAT es una lectura guardada hasta 30 segundos: refrescar el monitor no
            llama a SUNAT cada vez ni envía ningún comprobante.""")
    public ApiResponse<MonitorDeEmisionResponse> monitorear() {
        return ApiResponse.ok(MonitorDeEmisionResponse.de(monitor.monitorear()));
    }
}
