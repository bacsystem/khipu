package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MedioDePagoTest {
    /** H7: un mensaje para una persona dice «Transferencia», no «TRANSFERENCIA». */
    @Test void cadaMedioTieneSuNombreParaLasPersonas() {
        assertThat(Arrays.stream(MedioDePago.values()).map(MedioDePago::nombre))
                .containsExactly("Transferencia", "Depósito", "Yape", "Plin", "Tarjeta", "Efectivo", "Otro");
    }
}
