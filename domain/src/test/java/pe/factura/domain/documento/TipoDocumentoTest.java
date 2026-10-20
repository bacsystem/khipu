package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TipoDocumentoTest {
    @Test void facturaAceptaSerieF() {
        assertThat(TipoDocumento.FACTURA.serieValida("F001")).isTrue();
        assertThat(TipoDocumento.FACTURA.serieValida("FA01")).isTrue();
        assertThat(TipoDocumento.FACTURA.serieValida("B001")).isFalse();
        assertThat(TipoDocumento.FACTURA.serieValida("F01")).isFalse();
    }
    @Test void boletaAceptaSerieB() {
        assertThat(TipoDocumento.BOLETA.serieValida("B001")).isTrue();
        assertThat(TipoDocumento.BOLETA.serieValida("F001")).isFalse();
    }
    @Test void notaAceptaFoB() {
        assertThat(TipoDocumento.NOTA_CREDITO.serieValida("FC01")).isTrue();
        assertThat(TipoDocumento.NOTA_CREDITO.serieValida("BC01")).isTrue();
        assertThat(TipoDocumento.NOTA_CREDITO.serieValida("XC01")).isFalse();
    }
    /** #327: la liquidación de compra es la 04 con serie L### (regla 1001 de LiquidacionCompra2_0); ni F ni B, y una nota no la acepta. */
    @Test void liquidacionDeCompraAceptaSerieL() {
        assertThat(TipoDocumento.porCodigo("04")).isEqualTo(TipoDocumento.LIQUIDACION_COMPRA);
        assertThat(TipoDocumento.LIQUIDACION_COMPRA.serieValida("L001")).isTrue();
        assertThat(TipoDocumento.LIQUIDACION_COMPRA.serieValida("LA9Z")).isTrue();
        assertThat(TipoDocumento.LIQUIDACION_COMPRA.serieValida("F001")).isFalse();
        assertThat(TipoDocumento.LIQUIDACION_COMPRA.serieValida("0001")).as("la serie numérica es de la liquidación física, que khipu no emite").isFalse();
        assertThat(TipoDocumento.FACTURA.serieValida("L001")).isFalse();
        assertThat(TipoDocumento.NOTA_CREDITO.serieValida("L001")).isFalse();
    }
    @Test void codigoSunat() {
        assertThat(TipoDocumento.FACTURA.codigo()).isEqualTo("01");
        assertThat(TipoDocumento.porCodigo("01")).isEqualTo(TipoDocumento.FACTURA);
    }
}
