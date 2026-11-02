package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.EmpresaDetalle;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.domain.tenant.Entorno;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Detalle de una empresa del backoffice (#186), con Postgres real. Lo que más importa: no mezclar empresas y no exponer ningún secreto. */
class JdbcEmpresaDetalleAdminRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");
    static final LocalDate HOY = LocalDate.of(2026, 10, 3);

    JdbcEmpresasAdminRepository repo = new JdbcEmpresasAdminRepository(jdbc);

    UUID cuenta(String nombre, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, ?, ?, '987654321', ?)", id, nombre, email, Timestamp.from(T0));
        return id;
    }

    UUID empresa(UUID cuenta, String ruc, String razonSocial) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id, created_at) VALUES (?, ?, ?, 'BETA', ?, ?)", id, ruc, razonSocial, cuenta, Timestamp.from(T0));
        return id;
    }

    UUID empresa(String ruc) { return empresa(null, ruc, "EMPRESA " + ruc); }

    EmpresaDetalle detalle(UUID empresa) { return repo.detalle(empresa, HOY).orElseThrow(); }

    int numero = 0;

    UUID documento(UUID empresa, String serie, String fecha, String estado, Instant creado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, created_at)
                VALUES (?, ?, '01', ?, ?, ?::date, ?, ?, ?)""", id, empresa, serie, ++numero, fecha, estado, "doc-" + numero, Timestamp.from(creado));
        jdbc.update("""
                INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre,
                                         total_gravado, total_exonerado, total_inafecto, total_igv, total)
                VALUES (?, '0101', 'PEN', '6', '20601234565', 'CLIENTE SAC', 0, 0, 0, 0, 118.00)""", id);
        return id;
    }

    void evento(UUID documento, String anterior, String nuevo, String detalle, Instant cuando) {
        jdbc.update("INSERT INTO evento_documento (documento_id, estado_anterior, estado_nuevo, detalle, ocurrido_en) VALUES (?, ?, ?, ?, ?)",
                documento, anterior, nuevo, detalle, Timestamp.from(cuando));
    }

    // --- datos de la empresa -------------------------------------------------------------------------------------------------------

    @Test void unaEmpresaQueNoExisteNoTieneDetalle() {
        assertThat(repo.detalle(UUID.randomUUID(), HOY)).isEmpty();
    }

    @Test void traeLosDatosFiscalesYLosDeSuCuenta() {
        UUID ana = cuenta("Mi negocio", "ana@negocio.pe");
        UUID e = empresa(ana, "20100066603", "COMERCIAL ANDINA SAC");
        jdbc.update("""
                UPDATE tenant SET entorno = 'PRODUCCION', nombre_comercial = 'ANDINA', cuenta_detracciones = '00-123-456789', padron_tasa_especial_igv = true,
                       dom_ubigeo = '150122', dom_direccion = 'AV. LARCO 345', dom_urbanizacion = 'URB. SOL', dom_distrito = 'MIRAFLORES', dom_provincia = 'LIMA',
                       dom_departamento = 'LIMA', dom_establecimiento = '0000' WHERE id = ?""", e);

        EmpresaDetalle d = detalle(e);

        assertThat(d.id()).isEqualTo(e);
        assertThat(d.ruc()).isEqualTo("20100066603");
        assertThat(d.razonSocial()).isEqualTo("COMERCIAL ANDINA SAC");
        assertThat(d.nombreComercial()).isEqualTo("ANDINA");
        assertThat(d.entorno()).isEqualTo(Entorno.PRODUCCION);
        assertThat(d.creadaEn()).isEqualTo(T0);
        assertThat(d.cuentaId()).isEqualTo(ana);
        assertThat(d.cuentaNombre()).isEqualTo("Mi negocio");
        assertThat(d.cuentaDetracciones()).isEqualTo("00-123-456789");
        assertThat(d.padronTasaEspecialIgv()).isTrue();
        assertThat(d.domicilio()).satisfies(dom -> {
            assertThat(dom.ubigeo()).isEqualTo("150122");
            assertThat(dom.direccion()).isEqualTo("AV. LARCO 345");
            assertThat(dom.urbanizacion()).isEqualTo("URB. SOL");
            assertThat(dom.distrito()).isEqualTo("MIRAFLORES");
            assertThat(dom.provincia()).isEqualTo("LIMA");
            assertThat(dom.departamento()).isEqualTo("LIMA");
            assertThat(dom.codigoEstablecimiento()).isEqualTo("0000");
        });
    }

    @Test void unaEmpresaSinDomicilioNiCuentaLoDiceConNulos() {
        UUID e = empresa("20100066611");

        EmpresaDetalle d = detalle(e);

        assertThat(d.domicilio()).isNull();
        assertThat(d.cuentaId()).isNull();
        assertThat(d.cuentaNombre()).isNull();
        assertThat(d.nombreComercial()).isNull();
        assertThat(d.padronTasaEspecialIgv()).isFalse();
    }

    @Test void elCertificadoYLasCredencialesSolDicenSiEstanSinExponerlos() {
        UUID e = empresa("20100066603");
        jdbc.update("UPDATE tenant SET cert_pkcs12_enc = ?, cert_clave_enc = ?, cert_vigencia_hasta = '2026-10-13', sol_usuario_enc = ?, sol_clave_enc = ? WHERE id = ?",
                new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[]{4}, e);

        EmpresaDetalle d = detalle(e);

        assertThat(d.certificado()).isEqualTo(EstadoCertificado.POR_VENCER);
        assertThat(d.certificadoVigenteHasta()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(d.certificadoDiasRestantes()).isEqualTo(10);
        assertThat(d.tieneCredencialesSol()).isTrue();
    }

    /** El estado del certificado es el del listado: una sola regla, así las dos pantallas no se contradicen. */
    @Test void elEstadoDelCertificadoEsElMismoDelListadoParaCadaCaso() {
        for (String hasta : new String[]{null, "2026-10-02", "2026-10-03", "2026-11-01", "2026-11-02"}) {
            jdbc.update("TRUNCATE tenant CASCADE");
            UUID e = empresa("20100066603");
            jdbc.update("UPDATE tenant SET cert_pkcs12_enc = ?, cert_clave_enc = ?, cert_vigencia_hasta = ?::date WHERE id = ?", new byte[]{1}, new byte[]{2}, hasta, e);

            var delListado = repo.listar(pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro.NINGUNO, HOY, 1, 20).get(0);
            EmpresaDetalle d = detalle(e);

            assertThat(d.certificado()).as("hasta " + hasta).isEqualTo(delListado.certificado());
            assertThat(d.certificadoDiasRestantes()).as("hasta " + hasta).isEqualTo(delListado.certificadoDiasRestantes());
            assertThat(d.certificadoVigenteHasta()).as("hasta " + hasta).isEqualTo(delListado.certificadoVigenteHasta());
        }
    }

    @Test void sinCertificadoNiCredencialesLoDiceAsi() {
        EmpresaDetalle d = detalle(empresa("20100066611"));

        assertThat(d.certificado()).isEqualTo(EstadoCertificado.SIN_CERTIFICADO);
        assertThat(d.certificadoVigenteHasta()).isNull();
        assertThat(d.certificadoDiasRestantes()).isNull();
        assertThat(d.tieneCredencialesSol()).isFalse();
    }

    @Test void unasCredencialesSolALasMitadesNoCuentanComoCargadas() {
        UUID e = empresa("20100066611");
        jdbc.update("UPDATE tenant SET sol_usuario_enc = ? WHERE id = ?", new byte[]{3}, e);

        assertThat(detalle(e).tieneCredencialesSol()).isFalse();
    }

    // --- personalización del PDF -----------------------------------------------------------------------------------------------------

    @Test void laPersonalizacionDelPdfDiceSiHayLogoSinExponerDondeEstaGuardado() {
        UUID e = empresa("20100066603");
        jdbc.update("UPDATE tenant SET pdf_plantilla = 'MODERNO', pdf_color = '#0F766E', pdf_logo_key = 'logos/secreto-123.png', pdf_pie = 'Gracias', pdf_observaciones = 'Pago a 30 días' WHERE id = ?", e);

        var pdf = detalle(e).pdf();

        assertThat(pdf.plantilla()).isEqualTo("MODERNO");
        assertThat(pdf.colorPrimario()).isEqualTo("#0F766E");
        assertThat(pdf.tieneLogo()).isTrue();
        assertThat(pdf.pieDePagina()).isEqualTo("Gracias");
        assertThat(pdf.observacionesPorDefecto()).isEqualTo("Pago a 30 días");
        assertThat(pdf.toString()).doesNotContain("secreto-123");
    }

    @Test void sinPersonalizarSeVenLosValoresPorDefectoYSinLogo() {
        var pdf = detalle(empresa("20100066611")).pdf();

        assertThat(pdf.plantilla()).isEqualTo("CLASICO");
        assertThat(pdf.colorPrimario()).isEqualTo("#1E1E24");
        assertThat(pdf.tieneLogo()).isFalse();
        assertThat(pdf.pieDePagina()).isNull();
        assertThat(pdf.observacionesPorDefecto()).isNull();
    }

    // --- series, establecimientos y API keys -----------------------------------------------------------------------------------------

    @Test void lasSeriesSonLasDeLaEmpresaYNoDeOtras() {
        UUID a = empresa("20100066603");
        UUID b = empresa("20100066611");
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, ultimo_numero, activa, establecimiento) VALUES (?, '03', 'B001', 5, true, '0000')", a);
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, ultimo_numero, activa, establecimiento) VALUES (?, '01', 'F002', 0, false, '0001')", a);
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, ultimo_numero, activa, establecimiento) VALUES (?, '01', 'F001', 12, true, '0000')", a);
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, ultimo_numero) VALUES (?, '01', 'F001', 99)", b);

        var series = detalle(a).series();

        assertThat(series).extracting(s -> s.codigo()).as("por tipo y código").containsExactly("F001", "F002", "B001");
        assertThat(series.get(0).tipo()).isEqualTo("01");
        assertThat(series.get(0).ultimoNumero()).isEqualTo(12);
        assertThat(series.get(0).activa()).isTrue();
        assertThat(series.get(0).establecimiento()).isEqualTo("0000");
        assertThat(series.get(1).activa()).isFalse();
        assertThat(series.get(1).establecimiento()).isEqualTo("0001");
    }

    @Test void losEstablecimientosTraenSuDomicilioYSiEstanActivos() {
        UUID a = empresa("20100066603");
        UUID b = empresa("20100066611");
        jdbc.update("""
                INSERT INTO establecimiento (tenant_id, codigo, nombre, dom_ubigeo, dom_direccion, dom_distrito, dom_provincia, dom_departamento, activo)
                VALUES (?, '0002', 'Tienda Surco', '150140', 'AV. CAMINOS DEL INCA 100', 'SANTIAGO DE SURCO', 'LIMA', 'LIMA', false)""", a);
        jdbc.update("""
                INSERT INTO establecimiento (tenant_id, codigo, nombre, dom_ubigeo, dom_direccion, activo)
                VALUES (?, '0001', 'Tienda Miraflores', '150122', 'AV. LARCO 345', true)""", a);
        jdbc.update("INSERT INTO establecimiento (tenant_id, codigo, nombre, dom_ubigeo, dom_direccion) VALUES (?, '0001', 'De otra empresa', '150101', 'JR. X 1')", b);

        var est = detalle(a).establecimientos();

        assertThat(est).extracting(x -> x.nombre()).as("por código").containsExactly("Tienda Miraflores", "Tienda Surco");
        assertThat(est.get(0).activo()).isTrue();
        assertThat(est.get(0).domicilio().codigoEstablecimiento()).isEqualTo("0001");
        assertThat(est.get(0).domicilio().urbanizacion()).isNull();
        assertThat(est.get(1).activo()).isFalse();
        assertThat(est.get(1).domicilio().distrito()).isEqualTo("SANTIAGO DE SURCO");
    }

    /** De una API key solo se sabe su prefijo y si sigue vigente: ni el hash ni nada con lo que se pueda usar. */
    @Test void lasApiKeysDicenSuPrefijoYSiEstanVigentesSinExponerElHash() {
        UUID a = empresa("20100066603");
        UUID b = empresa("20100066611");
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa, created_at) VALUES (gen_random_uuid(), ?, ?, 'fk_vieja01', false, ?)",
                a, "a".repeat(64), Timestamp.from(T0));
        jdbc.execute("UPDATE api_key SET revoked_at = '2026-09-20T12:00:00Z' WHERE prefijo = 'fk_vieja01'");
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa, created_at) VALUES (gen_random_uuid(), ?, ?, 'fk_nueva02', true, ?)",
                a, "b".repeat(64), Timestamp.from(T0.plusSeconds(3600)));
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo) VALUES (gen_random_uuid(), ?, ?, 'fk_ajena03')", b, "c".repeat(64));

        var keys = detalle(a).apiKeys();

        assertThat(keys).extracting(k -> k.prefijo()).as("de la más nueva a la más vieja, sin las de otra empresa").containsExactly("fk_nueva02", "fk_vieja01");
        assertThat(keys.get(0).activa()).isTrue();
        assertThat(keys.get(0).revocadaEn()).isNull();
        assertThat(keys.get(1).activa()).isFalse();
        assertThat(keys.get(1).revocadaEn()).isEqualTo(Instant.parse("2026-09-20T12:00:00Z"));
        assertThat(keys.toString()).doesNotContain("a".repeat(64), "b".repeat(64));
    }

    // --- comprobantes recientes, con su CDR ------------------------------------------------------------------------------------------

    @Test void losComprobantesRecientesSonLosDiezUltimosDeLaEmpresaYNoDeOtras() {
        UUID a = empresa("20100066603");
        UUID b = empresa("20100066611");
        for (int i = 1; i <= 12; i++) documento(a, "F001", "2026-09-" + String.format("%02d", i), "ACEPTADO", T0.plusSeconds(i));
        documento(b, "F001", "2026-09-30", "ACEPTADO", T0.plusSeconds(500));

        var cs = detalle(a).comprobantes();

        assertThat(cs).as("tope de 10, de los más recientes a los más antiguos, solo de esta empresa").hasSize(10);
        assertThat(cs.get(0).fechaEmision()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(cs.get(9).fechaEmision()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test void cadaComprobanteTraeSuEstadoSusIntentosYElDetalleDelCdr() {
        UUID a = empresa("20100066603");
        UUID aceptada = documento(a, "F001", "2026-09-30", "ACEPTADO_CON_OBS", T0.plusSeconds(10));
        jdbc.update("""
                UPDATE documento SET intentos = 2, ultimo_error = 'timeout de SUNAT', cdr_codigo = '0', cdr_descripcion = 'La Factura ha sido aceptada',
                       cdr_observaciones = '["4287 - El dato ingresado no cumple", "2335 - Otra observación"]'::jsonb WHERE id = ?""", aceptada);
        UUID pendiente = documento(a, "F001", "2026-09-29", "FIRMADO", T0.plusSeconds(5));

        var cs = detalle(a).comprobantes();

        var c = cs.get(0);
        assertThat(c.id()).isEqualTo(aceptada);
        assertThat(c.tipo()).isEqualTo("01");
        assertThat(c.serie()).isEqualTo("F001");
        assertThat(c.estado()).isEqualTo("ACEPTADO_CON_OBS");
        assertThat(c.moneda()).isEqualTo("PEN");
        assertThat(c.total()).isEqualByComparingTo("118.00");
        assertThat(c.intentos()).isEqualTo(2);
        assertThat(c.ultimoError()).isEqualTo("timeout de SUNAT");
        assertThat(c.cdr().codigo()).isEqualTo("0");
        assertThat(c.cdr().descripcion()).isEqualTo("La Factura ha sido aceptada");
        assertThat(c.cdr().observaciones()).containsExactly("4287 - El dato ingresado no cumple", "2335 - Otra observación");
        var p = cs.get(1);
        assertThat(p.id()).isEqualTo(pendiente);
        assertThat(p.cdr()).as("SUNAT todavía no respondió").isNull();
        assertThat(p.ultimoError()).isNull();
        assertThat(p.intentos()).isZero();
    }

    @Test void unCdrSinObservacionesTraeUnaListaVacia() {
        UUID a = empresa("20100066603");
        UUID d = documento(a, "F001", "2026-09-30", "ACEPTADO", T0);
        jdbc.update("UPDATE documento SET cdr_codigo = '0', cdr_descripcion = 'Aceptada' WHERE id = ?", d);

        assertThat(detalle(a).comprobantes().get(0).cdr().observaciones()).isEmpty();
    }

    // --- eventos y outbox ------------------------------------------------------------------------------------------------------------

    /** Los eventos son los de los comprobantes que se muestran: no la historia entera de la empresa. */
    @Test void losEventosSonLosDeLosComprobantesRecientesDeLaEmpresa() {
        UUID a = empresa("20100066603");
        UUID b = empresa("20100066611");
        UUID viejo = documento(a, "F001", "2026-01-01", "ACEPTADO", T0.minusSeconds(100000));
        evento(viejo, "FIRMADO", "ACEPTADO", "de un comprobante que ya no es de los diez recientes", T0.plusSeconds(1));
        for (int i = 1; i <= 10; i++) {
            UUID d = documento(a, "F001", "2026-09-" + String.format("%02d", i), "ACEPTADO", T0.plusSeconds(i));
            evento(d, "RECIBIDO", "FIRMADO", "firmado " + i, T0.plusSeconds(100 + i));
        }
        UUID ajeno = documento(b, "F001", "2026-09-30", "ACEPTADO", T0);
        evento(ajeno, "RECIBIDO", "FIRMADO", "de otra empresa", T0.plusSeconds(999));

        var eventos = detalle(a).eventos();

        assertThat(eventos).extracting(e -> e.detalle()).doesNotContain("de un comprobante que ya no es de los diez recientes", "de otra empresa");
        assertThat(eventos).hasSize(10);
        assertThat(eventos.get(0).detalle()).as("el más reciente primero").isEqualTo("firmado 10");
        assertThat(eventos.get(0).serie()).isEqualTo("F001");
        assertThat(eventos.get(0).estadoAnterior()).isEqualTo("RECIBIDO");
        assertThat(eventos.get(0).estadoNuevo()).isEqualTo("FIRMADO");
        assertThat(eventos.get(0).ocurridoEn()).isEqualTo(T0.plusSeconds(110));
    }

    @Test void losEventosTienenUnTopeYElPrimeroPuedeNoTenerEstadoAnterior() {
        UUID a = empresa("20100066603");
        UUID d = documento(a, "F001", "2026-09-30", "ACEPTADO", T0);
        evento(d, null, "RECIBIDO", null, T0);
        for (int i = 1; i <= 25; i++) evento(d, "RECIBIDO", "FIRMADO", "n=" + i, T0.plusSeconds(i));

        var eventos = detalle(a).eventos();

        assertThat(eventos).as("tope de 20").hasSize(20);
        assertThat(eventos.get(0).detalle()).isEqualTo("n=25");
        assertThat(eventos.get(19).detalle()).isEqualTo("n=6");
    }

    @Test void sinComprobantesNoHayEventosNiSeConsultan() {
        assertThat(detalle(empresa("20100066603")).eventos()).isEmpty();
    }

    @Test void elOutboxCuentaLoPendienteYMuestraLasProximasTareas() {
        UUID a = empresa("20100066603");
        UUID b = empresa("20100066611");
        for (int i = 1; i <= 12; i++)
            jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, intentos, siguiente_intento, ultimo_error) VALUES (?, gen_random_uuid(), 'ENVIAR', ?, ?, ?)",
                    a, i, Timestamp.from(T0.plusSeconds(i * 60L)), i == 1 ? "SUNAT no responde" : null);
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, gen_random_uuid(), 'ENVIAR', ?)", b, Timestamp.from(T0));

        var outbox = detalle(a).outbox();

        assertThat(outbox.total()).as("el total es de esta empresa, no el tope de la lista").isEqualTo(12);
        assertThat(outbox.proximas()).hasSize(10);
        var primera = outbox.proximas().get(0);
        assertThat(primera.accion()).isEqualTo("ENVIAR");
        assertThat(primera.agregado()).isEqualTo("DOCUMENTO");
        assertThat(primera.intentos()).isEqualTo(1);
        assertThat(primera.siguienteIntento()).as("la próxima en intentarse, primero").isEqualTo(T0.plusSeconds(60));
        assertThat(primera.ultimoError()).isEqualTo("SUNAT no responde");
        assertThat(outbox.proximas().get(9).siguienteIntento()).isEqualTo(T0.plusSeconds(600));
    }

    @Test void sinTareasPendientesElOutboxEstaVacio() {
        var outbox = detalle(empresa("20100066603")).outbox();

        assertThat(outbox.total()).isZero();
        assertThat(outbox.proximas()).isEmpty();
    }

    // --- índices ---------------------------------------------------------------------------------------------------------------------

    /** Con los recorridos completos prohibidos, si el índice existe lo usa y si no, no tiene alternativa: se comprueba que el índice sirve. */
    private String plan(String consulta) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) con -> {
            try (java.sql.Statement st = con.createStatement()) {
                st.execute("SET enable_seqscan = off");
                StringBuilder texto = new StringBuilder();
                try (java.sql.ResultSet rs = st.executeQuery("EXPLAIN " + consulta)) {
                    while (rs.next()) texto.append(rs.getString(1)).append('\n');
                }
                return texto.toString();
            }
        });
    }

    @Test void lasApiKeysDeUnaEmpresaSeLeenPorIndice() {
        assertThat(plan("SELECT id FROM api_key WHERE tenant_id = '" + UUID.randomUUID() + "' ORDER BY created_at DESC")).contains("ix_api_key_tenant");
    }

    @Test void losEventosDeUnComprobanteSeLeenPorIndice() {
        assertThat(plan("SELECT id FROM evento_documento WHERE documento_id = '" + UUID.randomUUID() + "' ORDER BY ocurrido_en DESC LIMIT 20")).contains("ix_evento_documento");
    }

    @Test void losComprobantesRecientesSeLeenPorElIndiceDeLaEmpresa() {
        assertThat(plan("SELECT id FROM documento WHERE tenant_id = '" + UUID.randomUUID() + "' ORDER BY fecha_emision DESC, created_at DESC LIMIT 10")).contains("ix_documento_tenant_fecha");
    }

    @Test void lasSeriesYLosEstablecimientosSeLeenPorSuIndiceUnico() {
        assertThat(plan("SELECT id FROM serie WHERE tenant_id = '" + UUID.randomUUID() + "' ORDER BY tipo, codigo")).contains("serie_tenant_id_tipo_codigo_key");
        assertThat(plan("SELECT id FROM establecimiento WHERE tenant_id = '" + UUID.randomUUID() + "' ORDER BY codigo")).contains("establecimiento_tenant_id_codigo_key");
    }
}
