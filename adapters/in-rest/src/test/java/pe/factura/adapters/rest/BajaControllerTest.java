package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.TipoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BajaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class BajaControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DarDeBajaUseCase bajas;
    UUID tenant = UUID.randomUUID(), comprobante = UUID.randomUUID();

    static ComunicacionBaja baja(UUID tenant, UUID comprobante, ComunicacionBaja.EstadoBaja estado) {
        return ComunicacionBaja.rehidratar(UUID.randomUUID(), tenant, LocalDate.of(2026, 9, 18), 1, comprobante, TipoDocumento.FACTURA, "F001", 125, LocalDate.of(2026, 9, 15),
                "Error en el RUC del cliente", estado, "1789768174685", "k.xml", estado == ComunicacionBaja.EstadoBaja.ACEPTADA ? "r.zip" : null,
                estado == ComunicacionBaja.EstadoBaja.ACEPTADA ? new Cdr("0", "La Comunicacion de baja RA-20260918-1, ha sido aceptada", List.of()) : null, 1, null);
    }

    @Test void solicitaLaBajaYDevuelveElResultado() throws Exception {
        when(bajas.solicitar(eq(tenant), eq(comprobante), eq("Error en el RUC del cliente"))).thenReturn(baja(tenant, comprobante, ComunicacionBaja.EstadoBaja.ACEPTADA));
        mvc.perform(post("/v1/facturas/{id}/baja", comprobante).requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{\"motivo\":\"Error en el RUC del cliente\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.identificador").value("RA-20260918-1"))
                .andExpect(jsonPath("$.datos.comprobante").value("F001-125"))
                .andExpect(jsonPath("$.datos.estado").value("ACEPTADA"))
                .andExpect(jsonPath("$.datos.ticket").value("1789768174685"))
                .andExpect(jsonPath("$.datos.cdr.codigo").value("0"));
    }

    @Test void consultaUnaBajaEnCursoYLasDelComprobante() throws Exception {
        ComunicacionBaja enviada = baja(tenant, comprobante, ComunicacionBaja.EstadoBaja.ENVIADA);
        when(bajas.continuar(tenant, enviada.id())).thenReturn(enviada);
        when(bajas.deComprobante(tenant, comprobante)).thenReturn(List.of(enviada));
        mvc.perform(get("/v1/bajas/{id}", enviada.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ENVIADA"))
                .andExpect(jsonPath("$.datos.cdr").doesNotExist());
        mvc.perform(get("/v1/facturas/{id}/bajas", comprobante).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].identificador").value("RA-20260918-1"));
    }

    @Test void reglaDeNegocioYValidacion() throws Exception {
        when(bajas.solicitar(eq(tenant), eq(comprobante), any())).thenThrow(new DomainException("BAJA_INVALIDA", "2957 - El plazo para dar de baja F001-125 venció"));
        mvc.perform(post("/v1/facturas/{id}/baja", comprobante).requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{\"motivo\":\"Error en el RUC\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("BAJA_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("2957")));
        mvc.perform(post("/v1/facturas/{id}/baja", UUID.randomUUID()).requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{\"motivo\":\"ab\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['motivo']").exists());
        verify(bajas, never()).solicitar(any(), any(), eq("ab"));
    }
}
