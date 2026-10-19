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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las consultas de varias filas del repositorio de comprobantes (#9): cargan los cargos, ítems, anticipos, documentos relacionados y cuotas de **todas** las filas con una
 * consulta por tabla, no cinco por comprobante. La tabla del portal pedía 1 + 5·N consultas por página; el número ahora no depende de N, y cada comprobante vuelve igual que
 * con {@code buscar}.
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

    final Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    final JdbcComprobanteRepository escritura = new JdbcComprobanteRepository(jdbc);

    /** Una factura con de todo lo que vive en tablas hijas: cargos de línea y globales, cuotas, anticipos, guías y otros documentos. */
    Comprobante rica(UUID tenant, long numero, boolean alCredito) {
        Comprobante c = Comprobante.factura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE " + numero + " SAC", "AV 1"),
                        List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(Cargo.monto("48", new BigDecimal("5.00")))),
                                new Item("P2", "Mouse", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.GRAVADO)))
                .cargos(List.of(Cargo.monto("49", new BigDecimal("10.00"))))
                .anticipos(List.of(new Anticipo("F001", 3, new BigDecimal("30.00"), null, LocalDate.of(2026, 9, 1))))
                .referencias(new Referencias("OC-" + numero, List.of(new GuiaRelacionada("09", "T001-" + numero)), List.of(new DocumentoRelacionado("05", "SCOP-" + numero))))
                .formaPago(alCredito ? FormaPago.credito(new BigDecimal("125.00"), List.of(new FormaPago.Cuota(new BigDecimal("60.00"), LocalDate.of(2026, 10, 13)),
                        new FormaPago.Cuota(new BigDecimal("65.00"), LocalDate.of(2026, 11, 13)))) : FormaPago.contado())
                .crear(clock);
        c.asignarNumero(numero, "20100066603");
        c.firmar("H", "k.xml");
        return c;
    }

    List<Comprobante> sembrar(UUID tenant, int cuantos) {
        List<Comprobante> creados = new ArrayList<>();
        for (int i = 1; i <= cuantos; i++) {
            Comprobante c = rica(tenant, i, i % 2 == 0);
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

    @Test void elNumeroDeConsultasDeUnaPaginaNoDependeDeCuantosComprobantesTrae() {
        UUID t3 = tenantDePrueba(), t30 = tenantDePrueba();
        sembrar(t3, 3);
        sembrar(t30, 30);
        ContadorJdbc contador = contador();
        JdbcComprobanteRepository repo = new JdbcComprobanteRepository(contador);
        Filtro todos = new Filtro(null, null, null, null, null, null, null);

        int con3 = sentencias(contador, () -> assertThat(repo.listar(t3, todos, 1, 100)).hasSize(3));
        int con30 = sentencias(contador, () -> assertThat(repo.listar(t30, todos, 1, 100)).hasSize(30));

        assertThat(con30).as("30 comprobantes cuestan lo mismo que 3").isEqualTo(con3);
        assertThat(con30).as("ids + cinco tablas hijas + filas").isEqualTo(7);
    }

    @Test void cadaComprobanteDelListadoVuelveIgualQueConBuscar() {
        UUID t = tenantDePrueba();
        sembrar(t, 6);

        List<Comprobante> listados = escritura.listar(t, new Filtro(null, null, null, null, null, null, null), 1, 100);

        assertThat(listados).hasSize(6);
        for (Comprobante l : listados) {
            Comprobante uno = escritura.buscar(t, l.id()).orElseThrow();
            assertThat(l.items()).isEqualTo(uno.items());
            assertThat(l.cargos()).isEqualTo(uno.cargos());
            assertThat(l.anticipos()).isEqualTo(uno.anticipos());
            assertThat(l.referencias()).isEqualTo(uno.referencias());
            assertThat(l.formaPago().cuotas()).isEqualTo(uno.formaPago().cuotas());
            assertThat(l.formaPago().esCredito()).isEqualTo(uno.formaPago().esCredito());
            assertThat(l.totales().total()).isEqualByComparingTo(uno.totales().total());
            assertThat(l.receptor()).isEqualTo(uno.receptor());
        }
        Comprobante credito = listados.stream().filter(c -> c.numero() == 2).findFirst().orElseThrow();
        assertThat(credito.formaPago().cuotas()).hasSize(2);
        assertThat(credito.items().get(0).cargos()).containsExactly(Cargo.monto("48", new BigDecimal("5")));
        assertThat(credito.items().get(1).cargos()).isEmpty();
        assertThat(credito.cargos()).containsExactly(Cargo.monto("49", new BigDecimal("10")));
        assertThat(credito.referencias().guias()).extracting(GuiaRelacionada::numero).containsExactly("T001-2");
        assertThat(credito.referencias().otros()).extracting(DocumentoRelacionado::numero).containsExactly("SCOP-2");
    }

    @Test void losHijosNoSeMezclanEntreComprobantesDeLaMismaPagina() {
        UUID t = tenantDePrueba();
        sembrar(t, 5);

        List<Comprobante> listados = escritura.listar(t, new Filtro(null, null, null, null, null, null, null), 1, 100);

        for (Comprobante c : listados) {
            assertThat(c.items()).as("F001-%d", c.numero()).hasSize(2);
            assertThat(c.anticipos()).hasSize(1);
            assertThat(c.referencias().ordenCompra()).isEqualTo("OC-" + c.numero());
            assertThat(c.referencias().guias()).extracting(GuiaRelacionada::numero).containsExactly("T001-" + c.numero());
            assertThat(c.formaPago().cuotas()).hasSize(c.numero() % 2 == 0 ? 2 : 0);
        }
    }

    @Test void unaListaVaciaNoCargaHijosNiFalla() {
        UUID t = tenantDePrueba();
        ContadorJdbc contador = contador();
        JdbcComprobanteRepository repo = new JdbcComprobanteRepository(contador);

        int n = sentencias(contador, () -> assertThat(repo.listar(t, new Filtro(null, null, null, null, null, null, null), 1, 20)).isEmpty());

        assertThat(n).as("solo la consulta de ids y la de filas").isEqualTo(2);
    }

    @Test void masDeUnLoteDeHijosSeCargaEnVariasTandas() {
        UUID t = tenantDePrueba();
        // Con 520 comprobantes (más que el lote de 500) los hijos se leen en dos tandas y ninguno queda sin sus ítems.
        for (int i = 1; i <= 520; i++) {
            Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                    List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(clock);
            c.asignarNumero(i, "20100066603");
            c.firmar("H", "k.xml");
            escritura.guardar(c);
        }

        List<Comprobante> todos = escritura.firmadosEmitidosEntre(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(todos).hasSize(520);
        assertThat(todos).allSatisfy(c -> assertThat(c.items()).hasSize(1));
    }
}
