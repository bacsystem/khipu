package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;

import java.util.List;

public interface ControlarPlazoEnvioUseCase {
    /** Marca FUERA_DE_PLAZO los comprobantes no enviados cuyo plazo venció; devuelve los marcados. */
    List<Comprobante> marcarVencidos();
}
