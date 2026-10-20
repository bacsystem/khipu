package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.RegistroDeVentasSireUseCase;
import pe.factura.application.port.in.RegistroDeVentasSireUseCase.Conciliacion;
import pe.factura.application.port.in.RegistroDeVentasSireUseCase.Exportacion;
import pe.factura.domain.DomainException;
import pe.factura.domain.sire.ConciliacionRvie;
import pe.factura.domain.sire.PeriodoSire;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SireController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class SireControllerTest {
    @Autowired MockMvc mvc;
    @MockBean RegistroDeVentasSireUseCase registro;
    UUID tenant = UUID.randomUUID();

    Exportacion exportacion() {
        return new Exportacion(tenant, "20100066603", "EMPRESA", PeriodoSire.de("202610"), "LE2010006660320261000140400021112", 3, 2, 1);
    }

    @Test void descargaElZipConSuNombreYLasCuentasEnLasCabeceras() throws Exception {
        Exportacion e = exportacion();
        when(registro.preparar(tenant, "202610", false)).thenReturn(e);
        doAnswer(inv -> { inv.<OutputStream>getArgument(1).write("PK-zip".getBytes(StandardCharsets.UTF_8)); return null; }).when(registro).escribir(eq(e), any());

        var inicio = mvc.perform(get("/v1/sire/rvie?periodo=202610").requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(inicio)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"LE2010006660320261000140400021112.zip\""))
                .andExpect(header().string(SireController.CABECERA_REGISTROS, "3"))
                .andExpect(header().string(SireController.CABECERA_PENDIENTES, "2"))
                .andExpect(header().string(SireController.CABECERA_SIN_TIPO_CAMBIO, "1"))
                .andExpect(content().bytes("PK-zip".getBytes(StandardCharsets.UTF_8)));
    }

    @Test void permitirSinTipoDeCambioLlegaAlServicio() throws Exception {
        when(registro.preparar(tenant, "202610", true)).thenReturn(exportacion());
        mvc.perform(get("/v1/sire/rvie?periodo=202610&permitir_sin_tipo_cambio=true").requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(request().asyncStarted());
        verify(registro).preparar(tenant, "202610", true);
    }

    /** Lo que impide el archivo responde JSON con su código y su HTTP, antes de empezar el ZIP: escribir no llega a llamarse. */
    @Test void losErroresDePrepararSonJsonYNoEmpiezanElArchivo() throws Exception {
        record Caso(String codigo, int http) {}
        for (Caso c : List.of(new Caso("PARAMETRO_INVALIDO", 400), new Caso("NO_ENCONTRADO", 404), new Caso("RVIE_EXCEDE_EL_TOPE", 422), new Caso("RVIE_SIN_TIPO_CAMBIO", 422))) {
            reset(registro);
            when(registro.preparar(any(), any(), anyBoolean())).thenThrow(new DomainException(c.codigo(), "motivo " + c.codigo()));
            mvc.perform(get("/v1/sire/rvie?periodo=202610").requestAttr(TenantActual.ATRIBUTO, tenant))
                    .andExpect(status().is(c.http()))
                    .andExpect(header().doesNotExist("Content-Disposition"))
                    .andExpect(jsonPath("$.codigo").value(c.codigo()));
            verify(registro, never()).escribir(any(), any());
        }
    }

    @Test void sinPeriodoEsParametroInvalido() throws Exception {
        mvc.perform(get("/v1/sire/rvie").requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        mvc.perform(post("/v1/sire/rvie/conciliacion").requestAttr(TenantActual.ATRIBUTO, tenant).content("x")).andExpect(status().isBadRequest());
        verifyNoInteractions(registro);
    }

    @Test void conciliaElCuerpoTalCualLlegaYRespondeElReporte() throws Exception {
        AtomicReference<String> recibido = new AtomicReference<>();
        ConciliacionRvie.Resultado r = new ConciliacionRvie.Resultado(2, 2, 1, 1, 1, 1, 2, List.of("01-F001-9"), List.of("01-F001-3"),
                List.of(new ConciliacionRvie.Diferencia("01-F001-1", "total", "118.00", "120.00")));
        when(registro.conciliar(eq(tenant), eq("202610"), any())).thenAnswer(inv -> {
            recibido.set(new String(inv.<InputStream>getArgument(2).readAllBytes(), StandardCharsets.UTF_8));
            return new Conciliacion(PeriodoSire.de("202610"), 5, r);
        });

        mvc.perform(post("/v1/sire/rvie/conciliacion?periodo=202610").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("text/plain").content("20100066603|fila"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.periodo").value("202610"))
                .andExpect(jsonPath("$.datos.cuadra").value(false))
                .andExpect(jsonPath("$.datos.registros_khipu").value(2))
                .andExpect(jsonPath("$.datos.pendientes_de_sunat").value(5))
                .andExpect(jsonPath("$.datos.total_solo_en_sunat").value(1))
                .andExpect(jsonPath("$.datos.total_diferencias").value(2))
                .andExpect(jsonPath("$.datos.solo_en_sunat[0]").value("01-F001-9"))
                .andExpect(jsonPath("$.datos.solo_en_khipu[0]").value("01-F001-3"))
                .andExpect(jsonPath("$.datos.difieren[0].comprobante").value("01-F001-1"))
                .andExpect(jsonPath("$.datos.difieren[0].campo").value("total"))
                .andExpect(jsonPath("$.datos.difieren[0].khipu").value("118.00"))
                .andExpect(jsonPath("$.datos.difieren[0].sunat").value("120.00"));
        assertThat(recibido.get()).isEqualTo("20100066603|fila");
    }

    @Test void unArchivoQueNoSeLeeEs422ConSuMensaje() throws Exception {
        when(registro.conciliar(any(), any(), any())).thenThrow(new DomainException("RVIE_ARCHIVO_INVALIDO", "La propuesta del RVIE no se puede leer (línea 3): x"));
        mvc.perform(post("/v1/sire/rvie/conciliacion?periodo=202610").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/zip").content(new byte[]{1, 2}))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RVIE_ARCHIVO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("La propuesta del RVIE no se puede leer (línea 3): x"));
    }
}
