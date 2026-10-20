package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las consultas de varias filas del repositorio de comprobantes (#9): cargan los cargos, ítems, anticipos y documentos relacionados de **todas** las filas con una consulta por
 * tabla (y las cuotas solo de las que son al crédito), no cinco por comprobante. La tabla del portal pedía 1 + 5·N consultas por página; el número ahora no depende de N. Los datos
 * varían por comprobante y se comparan con lo que se sembró, no con otra lectura del mismo repositorio: un hijo que se asignara al comprobante equivocado, o fuera de orden, se vería.
 */
class JdbcComprobanteConsultasTest extends PersistenciaTestBase {
    /** Cuenta cada sentencia que se prepara en una conexión: es lo que de verdad viaja a la base, la haya pedido quien la haya pedido. */
    static class ContadorJdbc extends JdbcTemplate {
        final AtomicInteger sentencias;
        ContadorJdbc(javax.sql.DataSource original, AtomicInteger sentencias) {
            super(new org.springframework.jdbc.datasource.DelegatingDataSource(original) {
                @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                    java.sql.Connection real = super.getConnection();
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{java.sql.Connection.class}, (proxy, metodo, args) -> {
                        if (metodo.getName().equals("prepareStatement") || metodo.getName().equals("createStatement")) sentencias.incrementAndGet();
                        try {
                            return metodo.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
                }
            });
            this.sentencias = sentencias;
        }
    }

    static ContadorJdbc contador() { return new ContadorJdbc(jdbc.getDataSource(), new AtomicInteger()); }

    static final Filtro TODOS = new Filtro(null, null, null, null, null, null, null);
    final Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    final JdbcComprobanteRepository escritura = new JdbcComprobanteRepository(jdbc);

    static boolean alCredito(long n) { return n % 3 == 0; }
    static boolean conCargoEnLaPrimeraLinea(long n) { return n % 2 == 1; }

    /**
     * Una factura distinta por número: tres líneas con descripción propia, un cargo en la primera solo si el número es impar y siempre uno en la tercera, un cargo global, un anticipo,
     * guía, documento y orden de compra con el número, y dos cuotas si el número es múltiplo de 3.
     */
    Comprobante rica(UUID tenant, long n) {
        Function<FormaPago, Comprobante> armar = forma -> {
            Comprobante c = Comprobante.factura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE " + n + " SAC", "AV 1"),
                            List.of(new Item("P1", "Laptop " + n, "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.GRAVADO, null, null, false,
                                            conCargoEnLaPrimeraLinea(n) ? List.of(Cargo.monto("48", new BigDecimal(n + 1))) : List.of()),
                                    new Item("P2", "Mouse " + n, "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.GRAVADO),
                                    new Item("P3", "Cable " + n, "NIU", BigDecimal.ONE, new BigDecimal("20.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(Cargo.monto("48", new BigDecimal("3.00"))))))
                    .cargos(List.of(Cargo.monto("49", new BigDecimal("10.00"))))
                    .anticipos(List.of(new Anticipo("F001", n, new BigDecimal("30.00"), null, LocalDate.of(2026, 9, 1))))
                    .referencias(new Referencias("OC-" + n, List.of(new GuiaRelacionada("09", "T001-" + n)), List.of(new DocumentoRelacionado("05", "SCOP-" + n))))
                    .formaPago(forma)
                    .crear(clock);
            c.asignarNumero(n, "20100066603");
            c.firmar("H", "k.xml");
            return c;
        };
        Comprobante contado = armar.apply(FormaPago.contado());
        if (!alCredito(n)) return contado;
        BigDecimal total = contado.totales().total();
        BigDecimal primera = total.divide(new BigDecimal("2"), 2, java.math.RoundingMode.DOWN);
        return armar.apply(FormaPago.credito(total, List.of(new FormaPago.Cuota(primera, LocalDate.of(2026, 10, 13)), new FormaPago.Cuota(total.subtract(primera), LocalDate.of(2026, 11, 13)))));
    }

    List<Comprobante> sembrar(UUID tenant, int cuantos) {
        List<Comprobante> creados = new ArrayList<>();
        for (int i = 1; i <= cuantos; i++) {
            Comprobante c = rica(tenant, i);
            escritura.guardar(c);
            creados.add(c);
        }
        return creados;
    }

    int sentencias(ContadorJdbc contador, Runnable consulta) {
        contador.sentencias.set(0);
        consulta.run();
        return contador.sentencias.get();
    }

    void verificar(Comprobante leido, Comprobante sembrado) {
        long n = sembrado.numero();
        assertThat(leido.numero()).isEqualTo(n);
        assertThat(leido.items()).as("F001-%d: ítems en su orden", n).extracting(Item::descripcion).containsExactly("Laptop " + n, "Mouse " + n, "Cable " + n);
        assertThat(leido.items().get(0).cargos()).as("F001-%d: cargo de la primera línea", n).isEqualTo(conCargoEnLaPrimeraLinea(n) ? List.of(Cargo.monto("48", new BigDecimal(n + 1))) : List.of());
        assertThat(leido.items().get(1).cargos()).isEmpty();
        assertThat(leido.items().get(2).cargos()).containsExactly(Cargo.monto("48", new BigDecimal("3")));
        assertThat(leido.cargos()).as("cargo global").containsExactly(Cargo.monto("49", new BigDecimal("10")));
        assertThat(leido.anticipos()).extracting(Anticipo::comprobante).containsExactly("F001-" + n);
        assertThat(leido.referencias().ordenCompra()).isEqualTo("OC-" + n);
        assertThat(leido.referencias().guias()).extracting(GuiaRelacionada::numero).containsExactly("T001-" + n);
        assertThat(leido.referencias().otros()).extracting(DocumentoRelacionado::numero).containsExactly("SCOP-" + n);
        assertThat(leido.formaPago().esCredito()).isEqualTo(alCredito(n));
        if (alCredito(n)) {
            assertThat(leido.formaPago().cuotas()).as("cuotas en su orden").extracting(FormaPago.Cuota::vencimiento).containsExactly(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 11, 13));
            assertThat(leido.formaPago().cuotas().stream().map(FormaPago.Cuota::monto).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(sembrado.totales().total());
        } else {
            assertThat(leido.formaPago().cuotas()).isEmpty();
        }
        assertThat(leido.totales().total()).isEqualByComparingTo(sembrado.totales().total());
        assertThat(leido.receptor().razonSocial()).isEqualTo("CLIENTE " + n + " SAC");
    }

    @Test void elNumeroDeConsultasDeUnaPaginaNoDependeDeCuantosComprobantesTrae() {
        UUID t3 = tenantDePrueba(), t30 = tenantDePrueba();
        sembrar(t3, 3);
        sembrar(t30, 30);
        ContadorJdbc contador = contador();
        JdbcComprobanteRepository repo = new JdbcComprobanteRepository(contador);

        int con3 = sentencias(contador, () -> assertThat(repo.listar(t3, TODOS, 1, 100)).hasSize(3));
        int con30 = sentencias(contador, () -> assertThat(repo.listar(t30, TODOS, 1, 100)).hasSize(30));

        assertThat(con30).as("30 comprobantes cuestan lo mismo que 3").isEqualTo(con3);
        assertThat(con30).as("ids + cargos + ítems + anticipos + relacionados + cuotas + filas").isEqualTo(7);
    }

    @Test void unaPaginaSinNingunoAlCreditoNoPideLasCuotas() {
        UUID t = tenantDePrueba();
        List<Comprobante> contado = sembrar(t, 2).stream().filter(c -> !alCredito(c.numero())).toList();
        assertThat(contado).hasSize(2);
        ContadorJdbc contador = contador();
        JdbcComprobanteRepository repo = new JdbcComprobanteRepository(contador);

        int n = sentencias(contador, () -> assertThat(repo.listar(t, TODOS, 1, 100)).hasSize(2));

        assertThat(n).as("ids + cuatro tablas + filas").isEqualTo(6);
    }

    @Test void leerUnComprobanteAlContadoNoPideLasCuotasYUnoAlCreditoSi() {
        UUID t = tenantDePrueba();
        List<Comprobante> sembrados = sembrar(t, 3);
        ContadorJdbc contador = contador();
        JdbcComprobanteRepository repo = new JdbcComprobanteRepository(contador);
        UUID contado = sembrados.get(0).id(), credito = sembrados.get(2).id();

        int alContado = sentencias(contador, () -> assertThat(repo.buscar(t, contado)).isPresent());
        int alCredito = sentencias(contador, () -> assertThat(repo.buscar(t, credito)).isPresent());

        assertThat(alContado).as("la fila + cuatro tablas, las mismas que antes de este cambio").isEqualTo(5);
        assertThat(alCredito).isEqualTo(6);
    }

    @Test void cadaComprobanteDelListadoTraeSusPropiosHijosEnSuOrden() {
        UUID t = tenantDePrueba();
        List<Comprobante> sembrados = sembrar(t, 9);

        List<Comprobante> listados = escritura.listar(t, TODOS, 1, 100);

        assertThat(listados).hasSize(9);
        for (Comprobante l : listados) verificar(l, sembrados.get(l.numero().intValue() - 1));
    }

    @Test void lasOtrasConsultasDeVariasFilasTambienTraenLoSuyo() {
        UUID t = tenantDePrueba();
        List<Comprobante> sembrados = sembrar(t, 6);

        List<Comprobante> firmados = escritura.firmadosEmitidosEntre(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(firmados).hasSize(6);
        for (Comprobante c : firmados) verificar(c, sembrados.get(c.numero().intValue() - 1));
    }

    @Test void unaListaVaciaNoCargaHijosNiFalla() {
        UUID t = tenantDePrueba();
        ContadorJdbc contador = contador();
        JdbcComprobanteRepository repo = new JdbcComprobanteRepository(contador);

        int n = sentencias(contador, () -> assertThat(repo.listar(t, TODOS, 1, 20)).isEmpty());

        assertThat(n).as("solo la consulta de ids y la de filas").isEqualTo(2);
    }

    @Test void masDeUnLoteDeHijosSeCargaEnDosTandasCompletas() {
        UUID t = tenantDePrueba();
        // Con 520 comprobantes (más que el lote de 500) los hijos se leen en dos tandas; todos llegan con su ítem sin que ninguno se lea por demanda.
        for (int i = 1; i <= 520; i++) {
            Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                    List.of(new Item("P1", "Laptop " + i, "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(clock);
            c.asignarNumero(i, "20100066603");
            c.firmar("H", "k.xml");
            escritura.guardar(c);
        }
        ContadorJdbc contador = contador();
        JdbcComprobanteRepository repo = new JdbcComprobanteRepository(contador);
        List<Comprobante>[] todos = new List[1];

        int n = sentencias(contador, () -> todos[0] = repo.firmadosEmitidosEntre(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));

        assertThat(todos[0]).hasSize(520);
        assertThat(todos[0]).allSatisfy(c -> assertThat(c.items()).extracting(Item::descripcion).containsExactly("Laptop " + c.numero()));
        assertThat(n).as("ids + 2 tandas × 4 tablas + filas; ninguna se leyó por demanda").isEqualTo(1 + 2 * 4 + 1);
    }

    // --- #321: lectura por llave de la exportación -------------------------------------------------------------------------------

    Comprobante simple(UUID tenant, long n) {
        Comprobante c = Comprobante.factura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(clock);
        c.asignarNumero(n, "20100066603");
        c.firmar("H", "k.xml");
        escritura.guardar(c);
        return c;
    }

    List<Long> recorrerPorLlave(UUID tenant, Filtro f, java.util.Set<EstadoDocumento> excluidos, int pagina, Runnable entrePaginas) {
        List<Long> numeros = new ArrayList<>();
        pe.factura.application.port.in.ConsultarComprobanteUseCase.Llave despues = null;
        boolean primera = true;
        while (true) {
            var p = escritura.listarPorLlave(tenant, f, excluidos, despues, pagina);
            p.filas().forEach(c -> numeros.add(c.numero()));
            if (p.filas().size() < pagina) return numeros;
            despues = p.ultima();
            if (primera) { entrePaginas.run(); primera = false; }
        }
    }

    /** Con varios comprobantes creados en el mismo instante, el desempate por id hace que ninguno se repita ni se pierda entre páginas (con OFFSET y sin desempate, sí). */
    @Test void porLlaveConEmpatesDeCreacionCadaComprobanteSaleUnaVez() {
        UUID t = tenantDePrueba();
        for (long n = 1; n <= 7; n++) simple(t, n);
        jdbc.update("UPDATE documento SET created_at = '2026-09-13 10:00:00+00' WHERE tenant_id = ?", t);

        List<Long> numeros = recorrerPorLlave(t, TODOS, java.util.Set.of(), 2, () -> {});

        assertThat(numeros).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        // El listado por páginas también desempata por id: el mismo orden en las dos lecturas.
        List<Long> listado = new ArrayList<>();
        for (int p = 1; p <= 4; p++) escritura.listar(t, TODOS, p, 2).forEach(c -> listado.add(c.numero()));
        assertThat(listado).containsExactlyElementsOf(numeros);
    }

    /**
     * Filtrando por estado, uno de la primera página cambia de estado entre página y página (deja de cumplir el filtro). Con OFFSET la segunda página se correría una fila y uno
     * que no cambió se perdería; por llave salen todos los que no cambiaron.
     */
    @Test void porLlaveUnCambioDeEstadoEntrePaginasNoHacePerderOtrasFilas() {
        UUID t = tenantDePrueba();
        List<Comprobante> todos = new ArrayList<>();
        for (long n = 1; n <= 6; n++) {
            todos.add(simple(t, n));
            jdbc.update("UPDATE documento SET created_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.parse("2026-09-13T10:00:00Z").plusSeconds(n)), todos.get((int) n - 1).id());
        }
        Filtro firmados = new Filtro(EstadoDocumento.FIRMADO, null, null);

        // La primera página (lo más reciente primero) trae el 6 y el 5; antes de la segunda, el 6 pasa a ERROR_ENVIO.
        List<Long> numeros = recorrerPorLlave(t, firmados, java.util.Set.of(), 2,
                () -> jdbc.update("UPDATE documento SET estado = 'ERROR_ENVIO' WHERE id = ?", todos.get(5).id()));

        assertThat(numeros).containsExactly(6L, 5L, 4L, 3L, 2L, 1L);
    }

    @Test void porLlaveLosEstadosExcluidosNoSalenNiSeCuentan() {
        UUID t = tenantDePrueba();
        Comprobante rechazado = simple(t, 1);
        simple(t, 2);
        jdbc.update("UPDATE documento SET estado = 'RECHAZADO' WHERE id = ?", rechazado.id());
        var excluidos = java.util.Set.of(EstadoDocumento.RECHAZADO, EstadoDocumento.INVALIDO, EstadoDocumento.DESCARTADO);

        assertThat(recorrerPorLlave(t, TODOS, excluidos, 10, () -> {})).containsExactly(2L);
        assertThat(escritura.contar(t, TODOS, excluidos)).isEqualTo(1);
        assertThat(escritura.contar(t, TODOS, java.util.Set.of())).isEqualTo(2);
        assertThat(escritura.listarPorLlave(tenantDePrueba(), TODOS, excluidos, null, 10).filas()).as("otra empresa no ve nada").isEmpty();
    }
}
