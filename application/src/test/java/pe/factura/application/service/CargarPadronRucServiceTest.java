package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.FuentePadronRuc;
import pe.factura.application.port.out.PadronRucRepository;
import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;
import pe.factura.application.port.out.PadronRucRepository.Contribuyente;
import pe.factura.application.port.out.PadronRucRepository.ResultadoCarga;
import pe.factura.domain.DomainException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #361: la carga lee el archivo en ISO-8859-1, descarta lo que no es un renglón del padrón y no deja que un archivo cortado reemplace al bueno. */
class CargarPadronRucServiceTest {
    static final String ENCABEZADO = "RUC|NOMBRE O RAZÓN SOCIAL|ESTADO DEL CONTRIBUYENTE|CONDICIÓN DE DOMICILIO|UBIGEO|TIPO DE VÍA|NOMBRE DE VÍA|CÓDIGO DE ZONA|TIPO DE ZONA|NÚMERO|INTERIOR|LOTE|DEPARTAMENTO|MANZANA|KILÓMETRO|";
    static final String SIN_DOMICILIO = "|-|-|-|-|-|-|-|-|-|-|";
    final Clock clock = Clock.fixed(Instant.parse("2026-10-15T09:30:00Z"), ZoneOffset.UTC);
    final PadronFalso padron = new PadronFalso();

    CargarPadronRucService servicio(String archivo) {
        return new CargarPadronRucService(2, () -> fuente(archivo.getBytes(StandardCharsets.ISO_8859_1)), padron, clock);
    }

    static FuentePadronRuc.Descarga fuente(byte[] bytes) {
        return new FuentePadronRuc.Descarga() {
            public InputStream contenido() { return new ByteArrayInputStream(bytes); }
            public String origen() { return "archivo-de-prueba"; }
            public void close() { }
        };
    }

    @Test void cargaLasFilasSinElEncabezadoYLasLeeEnIso88591() {
        var carga = servicio(ENCABEZADO + "\n10452159428|MUÑOZ PEÑA JOSÉ|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n20100066603|TIENDAS S.A.C.|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n").cargar();

        assertThat(padron.recibidas).extracting(Contribuyente::razonSocial).containsExactly("MUÑOZ PEÑA JOSÉ", "TIENDAS S.A.C.");
        assertThat(carga.exitosa()).isTrue();
        assertThat(carga.filas()).isEqualTo(2);
        assertThat(carga.descartadas()).isZero();
        assertThat(carga.origen()).isEqualTo("archivo-de-prueba");
        assertThat(padron.registros).containsExactly(carga);
    }

    @Test void cuentaLasLineasQueNoSonUnRenglonYSigue() {
        var carga = servicio(ENCABEZADO + "\n10452159428|A|ACTIVO|HABIDO" + SIN_DOMICILIO + "\nbasura sin barras\n\n20100066603|B|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n123|C|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n").cargar();

        assertThat(padron.recibidas).hasSize(2);
        assertThat(carga.descartadas()).as("la basura y el RUC corto; ni el encabezado ni la línea en blanco").isEqualTo(2);
    }

    @Test void conUnaCargaAnteriorExigeElNoventaPorCientoDeSusFilas() {
        padron.ultima = new CargaRegistrada(Instant.parse("2026-10-14T09:30:00Z"), Instant.parse("2026-10-14T09:40:00Z"), true, "x", 1000, 0, 0, 0, 0, null);

        servicio(ENCABEZADO + "\n10452159428|A|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n").cargar();

        assertThat(padron.minimoPedido).isEqualTo(900);
    }

    @Test void laPrimeraCargaUsaElMinimoConfigurado() {
        servicio(ENCABEZADO + "\n10452159428|A|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n").cargar();

        assertThat(padron.minimoPedido).isEqualTo(2);
    }

    @Test void unArchivoCortadoDejaElPadronAnteriorYQuedaRegistradoComoFallido() {
        padron.fallo = new DomainException("PADRON_INCOMPLETO", "trae pocas filas");

        assertThatThrownBy(() -> servicio(ENCABEZADO + "\n10452159428|A|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n").cargar()).isSameAs(padron.fallo);

        assertThat(padron.registros).hasSize(1);
        assertThat(padron.registros.get(0).exitosa()).isFalse();
        assertThat(padron.registros.get(0).error()).contains("PADRON_INCOMPLETO");
    }

    @Test void siOtraCargaEstaCorriendoNoRegistraUnFallo() {
        padron.fallo = new DomainException("CARGA_EN_CURSO", "ya hay una");

        assertThatThrownBy(() -> servicio(ENCABEZADO + "\n").cargar()).isSameAs(padron.fallo);

        assertThat(padron.registros).isEmpty();
    }

    @Test void siNoSePuedeAbrirElArchivoDiceQueElPadronNoEstaDisponibleYLoRegistra() {
        var servicio = new CargarPadronRucService(2, () -> { throw new IOException("sin red"); }, padron, clock);

        assertThatThrownBy(servicio::cargar).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PADRON_NO_DISPONIBLE");

        assertThat(padron.registros).hasSize(1);
        assertThat(padron.registros.get(0).exitosa()).isFalse();
        assertThat(padron.registros.get(0).error()).contains("sin red");
    }

    @Test void unErrorInesperadoDeLaBaseSePropagaYQuedaRegistrado() {
        padron.fallo = new IllegalStateException("se cayó la base");

        assertThatThrownBy(() -> servicio(ENCABEZADO + "\n10452159428|A|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n").cargar()).isSameAs(padron.fallo);

        assertThat(padron.registros.get(0).error()).contains("se cayó la base");
    }

    @Test void siTampocoSePuedeRegistrarElFalloLaCausaOriginalNoSePierde() {
        padron.fallo = new IllegalStateException("se cayó la base");
        padron.falloAlRegistrar = new IllegalStateException("tampoco registra");

        assertThatThrownBy(() -> servicio(ENCABEZADO + "\n10452159428|A|ACTIVO|HABIDO" + SIN_DOMICILIO + "\n").cargar())
                .isSameAs(padron.fallo).hasSuppressedException(padron.falloAlRegistrar);
    }

    static final class PadronFalso implements PadronRucRepository {
        final List<Contribuyente> recibidas = new ArrayList<>();
        final List<CargaRegistrada> registros = new ArrayList<>();
        CargaRegistrada ultima;
        long minimoPedido = -1;
        RuntimeException fallo;
        RuntimeException falloAlRegistrar;

        public Optional<Contribuyente> buscar(String ruc) { throw new UnsupportedOperationException(); }
        public Optional<CargaRegistrada> ultimaCargaExitosa() { return Optional.ofNullable(ultima); }
        public ResultadoCarga reemplazar(Stream<Contribuyente> filas, long minimoDeFilas) {
            minimoPedido = minimoDeFilas;
            // Consume el flujo como lo haría el adaptador real, y recién después falla: así las descartadas ya están contadas.
            filas.forEach(recibidas::add);
            if (fallo != null) throw fallo;
            return new ResultadoCarga(recibidas.size(), recibidas.size(), 0, 0);
        }
        public void registrar(CargaRegistrada carga) {
            if (falloAlRegistrar != null) throw falloAlRegistrar;
            registros.add(carga);
        }
    }
}
