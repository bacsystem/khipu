package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.EstadoDocumento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El registro de ventas del SIRE (#322) solo lleva ventas ({@link pe.factura.domain.documento.TipoDocumento#esVenta()}): la liquidación de compra (04) va al registro de
 * compras, y la retención y la percepción (20, 40) no son ventas. Con Postgres real, sobre las mismas filas que el resumen.
 */
class JdbcRegistroDeVentasRepositoryTest extends PersistenciaTestBase {
    static final LocalDate DIA = LocalDate.of(2026, 9, 15);
    static final AtomicLong NUMERO = new AtomicLong(1);

    JdbcRegistroDeVentasRepository repo = new JdbcRegistroDeVentasRepository(jdbc, new JdbcComprobanteRepository(jdbc));

    void comprobante(UUID tenant, String tipo, EstadoDocumento estado, String moneda) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, ?, 'F001', ?, ?, ?, 'x')",
                id, tenant, tipo, NUMERO.getAndIncrement(), java.sql.Date.valueOf(DIA), estado.name());
        jdbc.update("INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total) "
                + "VALUES (?, '0101', ?, '6', '20601234565', 'CLIENTE', 0, 0, 0, 0, 100)", id, moneda);
    }

    @Test void soloCuentaLasVentasNiLaLiquidacionDeCompraNiLaRetencionNiLaPercepcion() {
        UUID t = tenantDePrueba();
        comprobante(t, "01", EstadoDocumento.ACEPTADO, "PEN");
        comprobante(t, "03", EstadoDocumento.ACEPTADO, "PEN");
        comprobante(t, "07", EstadoDocumento.ACEPTADO, "PEN");
        comprobante(t, "04", EstadoDocumento.ACEPTADO, "PEN");
        comprobante(t, "20", EstadoDocumento.ACEPTADO, "PEN");
        comprobante(t, "40", EstadoDocumento.ACEPTADO, "PEN");
        comprobante(t, "04", EstadoDocumento.FIRMADO, "PEN");
        comprobante(t, "04", EstadoDocumento.ACEPTADO, "USD");

        var conteo = repo.contar(t, DIA.withDayOfMonth(1), DIA.withDayOfMonth(30), 5);

        assertThat(conteo.incluidos()).isEqualTo(3);
        assertThat(conteo.pendientesDeSunat()).as("la liquidación FIRMADA no se avisa como venta pendiente").isZero();
        assertThat(conteo.sinTipoCambio()).as("la liquidación en dólares sin tipo de cambio no es del registro de ventas").isZero();
    }
}
