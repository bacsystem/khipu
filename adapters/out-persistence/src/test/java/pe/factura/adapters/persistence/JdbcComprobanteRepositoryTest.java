package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JdbcComprobanteRepositoryTest extends PersistenciaTestBase {
    JdbcComprobanteRepository repo = new JdbcComprobanteRepository(jdbc);
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    private Comprobante factura(UUID t, long numero) {
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"), List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))).crear(clock);
        c.asignarNumero(numero, "20100066603");
        return c;
    }

    @Test void guardaYRehidrataFormaPagoAlCredito() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).formaPago(FormaPago.credito(new BigDecimal("100.00"), List.of(
                        new FormaPago.Cuota(new BigDecimal("60.00"), LocalDate.of(2026, 10, 13)),
                        new FormaPago.Cuota(new BigDecimal("40.00"), LocalDate.of(2026, 11, 13))))).crear(clock);
        c.asignarNumero(3, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);

        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.formaPago().esCredito()).isTrue();
        assertThat(leido.formaPago().montoPendiente()).isEqualByComparingTo("100.00");
        assertThat(leido.formaPago().cuotas()).containsExactly(
                new FormaPago.Cuota(new BigDecimal("60.00"), LocalDate.of(2026, 10, 13)),
                new FormaPago.Cuota(new BigDecimal("40.00"), LocalDate.of(2026, 11, 13)));

        // Un update posterior (estado) no toca ni duplica las cuotas.
        leido.marcarEnviado();
        repo.guardar(leido);
        assertThat(repo.buscar(t, c.id()).orElseThrow().formaPago().cuotas()).hasSize(2);
    }

    @Test void guardaYRehidrataDescuentos() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(new BigDecimal("12.5"), true)),
                        new Item("P2", "Mouse", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO, Descuento.monto(new BigDecimal("5.00"), false)))).descuentoGlobal(Descuento.porcentaje(new BigDecimal("2"), true)).crear(clock);
        c.asignarNumero(5, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);

        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.items().get(0).descuento()).isEqualTo(Descuento.porcentaje(new BigDecimal("12.5"), true));
        assertThat(leido.items().get(1).descuento()).isEqualTo(Descuento.monto(new BigDecimal("5"), false));
        assertThat(leido.descuentoGlobal()).isEqualTo(Descuento.porcentaje(new BigDecimal("2"), true));
        assertThat(leido.totales().total()).isEqualByComparingTo(c.totales().total());
    }

    @Test void guardaYRehidrataCargos() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false,
                                List.of(Cargo.porcentaje("47", new BigDecimal("2.5")), Cargo.monto("48", new BigDecimal("5.00")))),
                        new Item("P2", "Mouse", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO))).cargos(List.of(Cargo.monto("49", new BigDecimal("10.00")), Cargo.porcentaje("46", new BigDecimal("10")))).crear(clock);
        c.asignarNumero(6, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);

        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.items().get(0).cargos()).containsExactly(Cargo.porcentaje("47", new BigDecimal("2.5")), Cargo.monto("48", new BigDecimal("5")));
        assertThat(leido.items().get(1).cargos()).isEmpty();
        assertThat(leido.cargos()).containsExactly(Cargo.monto("49", new BigDecimal("10")), Cargo.porcentaje("46", new BigDecimal("10")));
        assertThat(leido.totales().totalCargos()).isEqualByComparingTo(c.totales().totalCargos());
        assertThat(leido.totales().total()).isEqualByComparingTo(c.totales().total());
    }

    @Test void guardaYRehidrataReferencias() {
        UUID t = tenantDePrueba();
        Referencias refs = new Referencias("OC-2026-0457", List.of(new GuiaRelacionada("09", "T001-123"), new GuiaRelacionada("31", "V001-7")),
                List.of(new DocumentoRelacionado("05", "SCOP-8841203")));
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).referencias(refs).crear(clock);
        c.asignarNumero(7, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);

        assertThat(repo.buscar(t, c.id()).orElseThrow().referencias()).isEqualTo(refs);
        assertThat(repo.buscar(t, factura(t, 8).id())).isEmpty();
        Comprobante sin = factura(t, 8);
        sin.firmar("H", "k.xml");
        repo.guardar(sin);
        assertThat(repo.buscar(t, sin.id()).orElseThrow().referencias().vacias()).isTrue();
        // La clase solo puede ser GUIA u OTRO: una fila con otra clase no puede ni escribirse (CHECK) ni, si existiera, rehidratarse en silencio.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO comprobante_documento_relacionado (comprobante_id, orden, clase, tipo, numero) VALUES (?, 9, 'X', '09', 'T001-1')", c.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void guardaYRehidrataCamposOpcionales() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Diésel", "GLL", BigDecimal.ONE, new BigDecimal("118.37"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), new CodigoProductoSunat("15101505"), new Gtin("GTIN-13", "7750182000123")),
                        new Item("P2", "Mouse", "NIU", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO))).fechaVencimiento(LocalDate.of(2026, 10, 13)).redondeo(new BigDecimal("-0.37")).crear(clock);
        c.asignarNumero(9, "20100066603");
        c.firmar("H", "k.xml");
        c.anotar("Entrega en almacén central.\nHorario: 9 a 18 h.");
        repo.guardar(c);

        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.observaciones()).isEqualTo("Entrega en almacén central.\nHorario: 9 a 18 h.");
        assertThat(leido.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(leido.items().get(0).codigoSunat()).isEqualTo(new CodigoProductoSunat("15101505"));
        assertThat(leido.items().get(0).gtin()).isEqualTo(new Gtin("GTIN-13", "7750182000123"));
        assertThat(leido.items().get(1).codigoSunat()).isNull();
        assertThat(leido.items().get(1).gtin()).isNull();
        assertThat(leido.totales().redondeo()).isEqualByComparingTo("-0.37");
        assertThat(leido.totales().total()).isEqualByComparingTo("177.00");
        assertThat(leido.leyendas()).isEmpty();
        // Leyendas declaradas (#66): se guardan y vuelven en orden.
        Comprobante conLeyendas = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))).leyendas(List.of("2001", "2005")).crear(clock);
        conLeyendas.asignarNumero(10, "20100066603");
        repo.guardar(conLeyendas);
        assertThat(repo.buscar(t, conLeyendas.id()).orElseThrow().leyendas()).containsExactly("2001", "2005");
    }

    /** La tasa del IGV se guarda por comprobante: una factura al 10.5 % sigue al 10.5 % aunque la empresa salga del padrón (#84). */
    @Test void guardaYRehidrataLaTasaDelIgv() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Menú", "NIU", BigDecimal.ONE, new BigDecimal("110.50"), TipoAfectacionIgv.GRAVADO))).tasaIgv(new BigDecimal("10.50")).crear(clock);
        c.asignarNumero(11, "20100066603");
        repo.guardar(c);
        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.tasaIgv()).isEqualByComparingTo("10.50");
        assertThat(leido.totales().igv()).isEqualByComparingTo("10.50");
        assertThat(leido.totales().total()).isEqualByComparingTo("110.50");
        // Las existentes (sin tasa explícita) quedan al 18 %.
        Comprobante general = factura(t, 12);
        repo.guardar(general);
        assertThat(repo.buscar(t, general.id()).orElseThrow().tasaIgv()).isEqualByComparingTo("18.00");
    }

    /** Exportación (#65): país del receptor, Incoterm y país de uso sobreviven al round-trip; sin ellos vuelve null. */
    @Test void guardaYRehidrataExportacion() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "USD", "0201", new Receptor("0", "DE811", "BERLIN SOFT GMBH", null, "DE"),
                List.of(new Item("SRV", "Desarrollo de software", "ZZ", BigDecimal.ONE, new BigDecimal("5000.00"), TipoAfectacionIgv.EXPORTACION)))
                .exportacion(new Exportacion("DAP", "DE")).crear(clock);
        c.asignarNumero(13, "20100066603");
        repo.guardar(c);
        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.receptor().pais()).isEqualTo("DE");
        assertThat(leido.receptor().tipoDoc()).isEqualTo("0");
        assertThat(leido.exportacion()).isEqualTo(new Exportacion("DAP", "DE"));
        assertThat(leido.totales().exportacion()).isEqualByComparingTo("5000.00");
        assertThat(leido.totales().igv()).isEqualByComparingTo("0.00");
        assertThat(leido.items().get(0).afectacion()).isEqualTo(TipoAfectacionIgv.EXPORTACION);
        Comprobante interna = factura(t, 14);
        repo.guardar(interna);
        assertThat(repo.buscar(t, interna.id()).orElseThrow().exportacion()).isNull();
        assertThat(repo.buscar(t, interna.id()).orElseThrow().receptor().pais()).isNull();
    }

    /** Detracción sectorial (#69): hidrobiológico y transporte (con tramos y vehículos) sobreviven al round-trip como JSON; sin ellos vuelven null. */
    @Test void guardaYRehidrataDatosSectoriales() {
        UUID t = tenantDePrueba();
        Hidrobiologico h = new Hidrobiologico("CO-12345-PM", "DON JOSÉ II", "Anchoveta", "Muelle de Chimbote", LocalDate.of(2026, 9, 10), new BigDecimal("12.50"));
        Comprobante pesca = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "1002", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("ANCH", "Anchoveta", "TNE", new BigDecimal("12.5"), new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), null, null, h, null)))
                .detraccion(new Detraccion("004", new BigDecimal("4"), null, "00-000-123456", null)).crear(clock);
        pesca.asignarNumero(15, "20100066603");
        repo.guardar(pesca);
        Item leido = repo.buscar(t, pesca.id()).orElseThrow().items().get(0);
        assertThat(leido.hidrobiologico()).isEqualTo(h);
        assertThat(leido.transporte()).isNull();

        TransporteCarga tr = new TransporteCarga(new TransporteCarga.Punto("021801", "Av. Los Pescadores 450"), new TransporteCarga.Punto("150101", "Jr. de la Unión 100"),
                "Traslado de carga", new TransporteCarga.ValorReferencial(new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")),
                List.of(new TransporteCarga.Tramo("021801", "150101", "Chimbote – Lima", new BigDecimal("2400"), null, List.of(new TransporteCarga.Vehiculo("T3S3", new BigDecimal("30"), null)))));
        Comprobante flete = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "1004", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("FLT", "Flete", "ZZ", BigDecimal.ONE, new BigDecimal("2950.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), null, null, null, tr)))
                .detraccion(new Detraccion("027", new BigDecimal("4"), null, "00-000-123456", null)).crear(clock);
        flete.asignarNumero(16, "20100066603");
        repo.guardar(flete);
        Item leidoFlete = repo.buscar(t, flete.id()).orElseThrow().items().get(0);
        assertThat(leidoFlete.transporte()).isEqualTo(tr);
        assertThat(leidoFlete.transporte().tramos().get(0).vehiculos().get(0).configuracion()).isEqualTo("T3S3");
        assertThat(leidoFlete.hidrobiologico()).isNull();
        assertThat(repo.buscar(t, factura(t, 17).id()).isEmpty()).isTrue();
    }

    /** Control del plazo (#37): solo FIRMADO y ERROR_ENVIO con fecha de emisión hasta el corte, de cualquier empresa. */
    @Test void listaLosPendientesDeEnvioEmitidosHastaUnaFecha() {
        UUID t = tenantDePrueba();
        UUID otra = tenantDePrueba();
        Comprobante firmada = factura(t, 1); firmada.firmar("H", "k1.xml"); repo.guardar(firmada);
        Comprobante enError = factura(t, 2); enError.firmar("H", "k2.xml"); repo.guardar(enError);
        enError.marcarErrorEnvio("timeout"); repo.guardar(enError);   // ERROR_ENVIO se guarda condicional: primero debe existir como FIRMADO
        Comprobante aceptada = factura(t, 3); aceptada.firmar("H", "k3.xml"); repo.guardar(aceptada);
        aceptada.marcarEnviado(); aceptada.aplicarCdr(new pe.factura.domain.documento.Cdr("0", "ok", List.of()), "c3"); repo.guardar(aceptada);
        Comprobante ajena = factura(otra, 1); ajena.firmar("H", "k4.xml"); repo.guardar(ajena);
        Comprobante recibida = factura(t, 4); repo.guardar(recibida);   // RECIBIDO: sin firmar, no entra

        assertThat(repo.pendientesDeEnvioEmitidosHasta(LocalDate.of(2026, 9, 13))).extracting(Comprobante::id)
                .containsExactlyInAnyOrder(firmada.id(), enError.id(), ajena.id());
        assertThat(repo.pendientesDeEnvioEmitidosHasta(LocalDate.of(2026, 9, 12))).isEmpty();
        firmada.marcarFueraDePlazo(LocalDate.of(2026, 9, 17));
        repo.guardar(firmada);
        assertThat(repo.buscar(t, firmada.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.FUERA_DE_PLAZO);
        assertThat(repo.pendientesDeEnvioEmitidosHasta(LocalDate.of(2026, 9, 13))).extracting(Comprobante::id).containsExactlyInAnyOrder(enError.id(), ajena.id());
    }

    /** Recuperación de CDR (#36): firmados sin cdr_key en estados donde SUNAT pudo haberlo recibido. */
    @Test void listaLosPendientesDeCdr() {
        UUID t = tenantDePrueba();
        Comprobante firmada = factura(t, 1); firmada.firmar("H", "k1.xml"); repo.guardar(firmada);   // FIRMADO: nunca se envió, no entra
        Comprobante enviada = factura(t, 2); enviada.firmar("H", "k2.xml"); repo.guardar(enviada); enviada.marcarEnviado(); repo.guardar(enviada);
        Comprobante aceptadaSinCdr = factura(t, 3); aceptadaSinCdr.firmar("H", "k3.xml"); repo.guardar(aceptadaSinCdr);
        aceptadaSinCdr.marcarEnviado(); aceptadaSinCdr.aplicarCdr(new pe.factura.domain.documento.Cdr("0", "ok", List.of()), null); repo.guardar(aceptadaSinCdr);
        Comprobante conCdr = factura(t, 4); conCdr.firmar("H", "k4.xml"); repo.guardar(conCdr);
        conCdr.marcarEnviado(); conCdr.aplicarCdr(new pe.factura.domain.documento.Cdr("0", "ok", List.of()), "R-4.zip"); repo.guardar(conCdr);
        assertThat(repo.pendientesDeCdr()).extracting(Comprobante::id).containsExactlyInAnyOrder(enviada.id(), aceptadaSinCdr.id());
    }

    /** Integridad del storage (#38): solo los firmados (con xml_key) del rango, de cualquier empresa. */
    @Test void listaLosFirmadosEmitidosEntreDosFechas() {
        UUID t = tenantDePrueba(), otro = tenantDePrueba();
        Comprobante sinFirma = factura(t, 1); repo.guardar(sinFirma);
        Comprobante firmada = factura(t, 2); firmada.firmar("H", "k2.xml"); repo.guardar(firmada);
        Comprobante deOtro = factura(otro, 1); deOtro.firmar("H", "k3.xml"); repo.guardar(deOtro);
        Comprobante antigua = Comprobante.factura(t, "F001", LocalDate.of(2026, 8, 31), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(java.time.Clock.fixed(java.time.Instant.parse("2026-08-31T15:00:00Z"), java.time.ZoneId.of("America/Lima")));
        antigua.asignarNumero(3, "20100066603"); antigua.firmar("H", "k4.xml"); repo.guardar(antigua);
        assertThat(repo.firmadosEmitidosEntre(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).extracting(Comprobante::id).containsExactlyInAnyOrder(firmada.id(), deOtro.id());
        assertThat(repo.firmadosEmitidosEntre(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).extracting(Comprobante::id).containsExactly(antigua.id());
    }

    /** Filtro por rango de fechas (#2): bordes inclusive, rango abierto, combinado con estado; el conteo usa el mismo filtro. */
    @Test void listaYCuentaPorRangoDeFechasYEstado() {
        UUID t = tenantDePrueba();
        var lima = java.time.ZoneId.of("America/Lima");
        for (String dia : List.of("2026-09-01", "2026-09-10", "2026-09-13")) {
            Comprobante c = Comprobante.factura(t, "F001", LocalDate.parse(dia), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                    List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)))
                    .crear(java.time.Clock.fixed(LocalDate.parse(dia).atTime(15, 0).atZone(lima).toInstant(), lima));
            c.asignarNumero(Long.parseLong(dia.substring(8)), "20100066603");
            if (dia.equals("2026-09-10")) { c.firmar("H", "k.xml"); }
            repo.guardar(c);
        }
        assertThat(repo.listar(t, new Filtro(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)), 1, 10)).extracting(Comprobante::numero).containsExactlyInAnyOrder(1L, 10L);
        assertThat(repo.contar(t, new Filtro(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)))).isEqualTo(2);
        assertThat(repo.listar(t, new Filtro(null, LocalDate.of(2026, 9, 11), null), 1, 10)).extracting(Comprobante::numero).containsExactly(13L);
        assertThat(repo.listar(t, new Filtro(null, null, LocalDate.of(2026, 9, 9)), 1, 10)).extracting(Comprobante::numero).containsExactly(1L);
        assertThat(repo.contar(t, new Filtro(EstadoDocumento.FIRMADO, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))).isEqualTo(1);
        assertThat(repo.contar(t, new Filtro(EstadoDocumento.FIRMADO, LocalDate.of(2026, 9, 11), null))).isZero();
        assertThat(repo.contar(t, Filtro.NINGUNO)).isEqualTo(3);
        // Serie (#3): exacta; inexistente → vacío y 0
        Comprobante otraSerie = Comprobante.factura(t, "F002", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(clock);
        otraSerie.asignarNumero(1, "20100066603"); repo.guardar(otraSerie);
        assertThat(repo.listar(t, new Filtro(null, null, null, "F002"), 1, 10)).extracting(Comprobante::id).containsExactly(otraSerie.id());
        assertThat(repo.contar(t, new Filtro(null, LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), "F001"))).isEqualTo(1);
        assertThat(repo.contar(t, new Filtro(null, null, null, "F999"))).isZero();
    }

    @Test void guardaYRehidrataNotasYLasListaPorFactura() {
        UUID t = tenantDePrueba();
        Comprobante f = factura(t, 20);
        f.firmar("H", "k.xml");
        repo.guardar(f);
        Nota nota = new Nota(TipoDocumento.FACTURA, "F001", 20, "07", "Devolución de una laptop");
        Comprobante nc = Comprobante.nota(t, TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), nota, f.receptor(), List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(clock);
        nc.asignarNumero(1, "20100066603");
        nc.firmar("H", "nc.xml");
        repo.guardar(nc);

        Comprobante leida = repo.buscar(t, nc.id()).orElseThrow();
        assertThat(leida.tipo()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(leida.nota()).isEqualTo(nota);
        assertThat(leida.nombreArchivo()).isEqualTo("20100066603-07-FC01-1");
        assertThat(repo.buscar(t, f.id()).orElseThrow().nota()).isNull();
        assertThat(repo.notasDe(t, "F001", 20)).extracting(Comprobante::id).containsExactly(nc.id());
        assertThat(repo.notasDe(t, "F001", 21)).isEmpty();
        assertThat(repo.buscarPorNumero(t, TipoDocumento.NOTA_CREDITO, "FC01", 1)).isPresent();
        assertThat(repo.buscarPorNumero(t, TipoDocumento.FACTURA, "FC01", 1)).isEmpty();
    }

    @Test void comunicacionDeBaja_guardaRehidrataYNumeraPorDia() {
        UUID t = tenantDePrueba();
        Comprobante f = factura(t, 30);
        f.firmar("H", "k.xml"); f.marcarEnviado(); f.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr.zip");
        repo.guardar(f);
        JdbcBajaRepository bajas = new JdbcBajaRepository(jdbc);
        Clock hoy = Clock.fixed(Instant.parse("2026-09-15T15:00:00Z"), ZoneId.of("America/Lima"));
        assertThat(bajas.siguienteCorrelativo(t, LocalDate.of(2026, 9, 15))).isEqualTo(1);
        ComunicacionBaja b = ComunicacionBaja.crear(f, 1, "Error en el RUC del cliente", hoy);
        b.firmar("ra.xml");
        bajas.guardar(b);
        assertThat(bajas.siguienteCorrelativo(t, LocalDate.of(2026, 9, 15))).isEqualTo(2);
        assertThat(bajas.siguienteCorrelativo(t, LocalDate.of(2026, 9, 16))).isEqualTo(1);

        b.marcarEnviada("1789768174685");
        b.aplicarCdr(new Cdr("0", "La Comunicacion de baja RA-20260915-1, ha sido aceptada", List.of()), "r.zip");
        bajas.guardar(b);
        ComunicacionBaja leida = bajas.buscar(t, b.id()).orElseThrow();
        assertThat(leida.identificador()).isEqualTo("RA-20260915-1");
        assertThat(leida.estado()).isEqualTo(ComunicacionBaja.EstadoBaja.ACEPTADA);
        assertThat(leida.ticket()).isEqualTo("1789768174685");
        assertThat(leida.cdr().codigo()).isEqualTo("0");
        assertThat(leida.comprobanteId()).isEqualTo(f.id());
        assertThat(leida.fechaReferencia()).isEqualTo(f.fechaEmision());
        assertThat(bajas.deComprobante(t, f.id())).extracting(ComunicacionBaja::id).containsExactly(b.id());
        assertThat(bajas.buscar(UUID.randomUUID(), b.id())).isEmpty();
        // El comprobante puede bloquearse por id y pasar a ANULADO.
        Comprobante bloqueado = repo.bloquear(t, f.id()).orElseThrow();
        bloqueado.anular();
        repo.guardar(bloqueado);
        assertThat(repo.buscar(t, f.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ANULADO);
    }

    @Test void guardaYRehidrataDetraccion() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "1001", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO))).detraccion(new Detraccion("022", new BigDecimal("12.5"), new BigDecimal("148.00"), "00-000-123456", "003")).crear(clock);
        c.asignarNumero(6, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);
        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.detraccion()).isEqualTo(new Detraccion("022", new BigDecimal("12.5"), new BigDecimal("148.00"), "00-000-123456", "003"));
        assertThat(leido.tipoOperacion()).isEqualTo("1001");
    }

    @Test void guardaYRehidrataRetencionYPercepcion() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "2001", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO))).retencion(new RetencionIgv(new BigDecimal("3"), null)).percepcion(new Percepcion("52", null, null, null)).crear(clock);
        c.asignarNumero(7, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);
        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.retencion()).isEqualTo(new RetencionIgv(new BigDecimal("3"), new BigDecimal("35.40")));
        assertThat(leido.percepcion()).isEqualTo(new Percepcion("52", new BigDecimal("1"), new BigDecimal("1180.00"), new BigDecimal("11.80")));
    }

    @Test void guardaYRehidrataIscEIcbper() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("C", "Cerveza", "NIU", BigDecimal.ONE, new BigDecimal("159.30"), TipoAfectacionIgv.GRAVADO, null, new Isc("02", null, new BigDecimal("2.25")), false),
                        new Item("B", "Bolsa", "NIU", new BigDecimal("3"), new BigDecimal("0.618"), TipoAfectacionIgv.GRAVADO, null, null, true))).crear(clock);
        c.asignarNumero(8, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);
        Comprobante leido = repo.buscar(t, c.id()).orElseThrow();
        assertThat(leido.items().get(0).isc()).isEqualTo(new Isc("02", null, new BigDecimal("2.25")));
        assertThat(leido.items().get(0).icbper()).isFalse();
        assertThat(leido.items().get(1).isc()).isNull();
        assertThat(leido.items().get(1).icbper()).isTrue();
        assertThat(leido.totales().total()).isEqualByComparingTo(c.totales().total());
    }

    @Test void guardaYRehidrataAnticiposYBuscaPorNumero() {
        UUID t = tenantDePrueba();
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("O", "Obra", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("E", "Exonerado", "NIU", BigDecimal.ONE, new BigDecimal("200.00"), TipoAfectacionIgv.EXONERADO))).anticipos(List.of(new Anticipo("F001", 3, new BigDecimal("300.00"), null, LocalDate.of(2026, 9, 1)),
                        new Anticipo("F002", 4, new BigDecimal("50.00"), Anticipo.Afectacion.EXONERADO, null))).crear(clock);
        c.asignarNumero(9, "20100066603");
        c.firmar("H", "k.xml");
        repo.guardar(c);
        Comprobante leido = repo.buscarPorNumero(t, TipoDocumento.FACTURA, "F001", 9).orElseThrow();
        assertThat(leido.id()).isEqualTo(c.id());
        assertThat(leido.horaEmision()).isEqualTo(java.time.LocalTime.of(10, 0));   // 15:00Z en America/Lima
        assertThat(leido.anticipos()).containsExactly(
                new Anticipo("F001", 3, new BigDecimal("300.00"), Anticipo.Afectacion.GRAVADO, LocalDate.of(2026, 9, 1)),
                new Anticipo("F002", 4, new BigDecimal("50.00"), Anticipo.Afectacion.EXONERADO, null));
        assertThat(leido.totales().totalAnticipos()).isEqualByComparingTo("404.00");
        assertThat(leido.totales().total()).isEqualByComparingTo("976.00");   // 1380 − 404
        assertThat(repo.buscarPorNumero(t, TipoDocumento.FACTURA, "F001", 99)).isEmpty();
        assertThat(repo.bloquearPorNumero(t, TipoDocumento.FACTURA, "F001", 9)).map(Comprobante::id).hasValue(c.id());
        // Lo regularizado del anticipo F001-3 suma solo lo de finales no rechazadas
        assertThat(repo.montoRegularizado(t, "F001", 3)).isEqualByComparingTo("300.00");
        assertThat(repo.montoRegularizado(t, "F002", 4)).isEqualByComparingTo("50.00");
        assertThat(repo.montoRegularizado(t, "F001", 99)).isEqualByComparingTo("0");
        c.marcarEnviado(); c.rechazarPorFault("2335", "rechazado");
        repo.guardar(c);
        assertThat(repo.montoRegularizado(t, "F001", 3)).isEqualByComparingTo("0");
    }

    @Test void guardaYRehidrataCompleto() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 1);
        c.firmar("HASH==", t + "/2026/09/20100066603-01-F001-1.xml");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of("4252 - obs")), "k/cdr.zip");
        repo.guardar(c);

        Comprobante r = repo.buscar(t, c.id()).orElseThrow();
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ACEPTADO_CON_OBS);
        assertThat(r.numero()).isEqualTo(1L);
        assertThat(r.hash()).isEqualTo("HASH==");
        assertThat(r.items()).hasSize(2);
        assertThat(r.items().get(1).afectacion()).isEqualTo(TipoAfectacionIgv.EXONERADO);
        assertThat(r.totales().total()).isEqualByComparingTo("286.00");
        assertThat(r.cdr().observaciones()).containsExactly("4252 - obs");
        assertThat(r.cdrKey()).isEqualTo("k/cdr.zip");
        assertThat(r.receptor().razonSocial()).isEqualTo("CLIENTE SAC");
    }

    @Test void existeYUnicidad() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 5); c.firmar("h", "k"); repo.guardar(c);
        assertThat(repo.buscarPorNumero(t, TipoDocumento.FACTURA, "F001", 5)).isPresent();
        assertThat(repo.buscarPorNumero(t, TipoDocumento.FACTURA, "F001", 6)).isEmpty();
        Comprobante dup = factura(t, 5); dup.firmar("h", "k");
        assertThatThrownBy(() -> repo.guardar(dup)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void aislamientoPorTenant() {
        UUID t1 = tenantDePrueba(), t2 = tenantDePrueba();
        Comprobante c = factura(t1, 1); c.firmar("h", "k"); repo.guardar(c);
        assertThat(repo.buscar(t2, c.id())).isEmpty();
        assertThat(repo.listar(t1, Filtro.NINGUNO, 1, 10)).hasSize(1);
        assertThat(repo.listar(t2, Filtro.NINGUNO, 1, 10)).isEmpty();
    }

    @Test void observacionesConSaltosDeLineaSobrevivenAlRoundTrip() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 7);
        c.firmar("h", "k");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of("4252 - obs\ncon salto", "otra\tcon tab")), "k/cdr.zip");
        repo.guardar(c);

        Comprobante r = repo.buscar(t, c.id()).orElseThrow();
        assertThat(r.cdr().observaciones()).containsExactly("4252 - obs\ncon salto", "otra\tcon tab");
    }

    @Test void unEnvioTardioNoPisaUnEstadoTerminal() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 9);
        c.firmar("h", "k");
        repo.guardar(c);
        c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "k/cdr.zip");
        repo.guardar(c);

        // Copia rehidratada "vieja" (leída antes de que otra transacción persistiera ACEPTADO) que falla al enviar
        Comprobante tardio = Comprobante.persistido(c.id(), t, TipoDocumento.FACTURA, "F001", 9L, LocalDate.of(2026, 9, 13), EstadoDocumento.ERROR_ENVIO, c.receptor(), c.items()).horaEmision(c.horaEmision()).firma("h", c.nombreArchivo(), "k").envio(1, "0109 - timeout").rehidratar();
        assertThatThrownBy(() -> repo.guardar(tardio))
                .isInstanceOf(pe.factura.domain.DomainException.class).extracting("codigo").isEqualTo("ESTADO_CONFLICTO");
        assertThat(repo.buscar(t, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documento WHERE id = ?", Integer.class, c.id())).isEqualTo(1);

        Comprobante enviadoTardio = Comprobante.persistido(c.id(), t, TipoDocumento.FACTURA, "F001", 9L, LocalDate.of(2026, 9, 13), EstadoDocumento.ENVIADO, c.receptor(), c.items()).horaEmision(c.horaEmision()).firma("h", c.nombreArchivo(), "k").envio(1, null).rehidratar();
        assertThatThrownBy(() -> repo.guardar(enviadoTardio)).extracting("codigo").isEqualTo("ESTADO_CONFLICTO");
        assertThat(repo.buscar(t, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ACEPTADO);
    }

    @Test void errorEnvioSobreFirmadoOErrorEnvioSiSeGuarda() {
        UUID t = tenantDePrueba();
        Comprobante c = factura(t, 11);
        c.firmar("h", "k");
        repo.guardar(c);
        c.marcarErrorEnvio("0109 - timeout");
        repo.guardar(c);
        assertThat(repo.buscar(t, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ERROR_ENVIO);
        c.marcarErrorEnvio("0109 - timeout otra vez");
        repo.guardar(c);
        assertThat(repo.buscar(t, c.id()).orElseThrow().intentos()).isEqualTo(2);
        c.marcarEnviado();
        repo.guardar(c);
        assertThat(repo.buscar(t, c.id()).orElseThrow().estado()).isEqualTo(EstadoDocumento.ENVIADO);
    }
}
