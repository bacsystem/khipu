package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.ProductoGuardado;
import pe.factura.domain.documento.ProductoGuardado.Datos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Los productos guardados (#320) con Postgres real: código interno único por empresa si existe, búsqueda por código y descripción, y aislamiento entre empresas. */
class JdbcProductoRepositoryTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");

    JdbcProductoRepository repo = new JdbcProductoRepository(jdbc);

    ProductoGuardado producto(UUID tenant, String codigo, String descripcion) {
        ProductoGuardado p = ProductoGuardado.nuevo(tenant, new Datos(codigo, descripcion, "ZZ", "10", new BigDecimal("118"), "80101500"), AHORA);
        assertThat(repo.crear(p)).isTrue();
        return p;
    }

    @Test void guardaYLeeTodosLosCamposConElPrecioSinCerosDeRelleno() {
        UUID t = tenantDePrueba();
        ProductoGuardado p = ProductoGuardado.nuevo(t, new Datos("SKU-1", "Servicio de consultoría", "zz", "10", new BigDecimal("118.5"), "80101500"), AHORA);
        assertThat(repo.crear(p)).isTrue();

        assertThat(repo.buscar(t, p.id())).contains(p);
        assertThat(repo.buscar(t, p.id()).orElseThrow().precioUnitario().toPlainString()).isEqualTo("118.50");
        assertThat(repo.buscar(t, p.id()).orElseThrow().unidad()).isEqualTo("ZZ");
    }

    @Test void unPrecioConDecimalesDeSobraSeGuardaEntero() {
        UUID t = tenantDePrueba();
        ProductoGuardado p = ProductoGuardado.nuevo(t, new Datos(null, "Bolsa", "NIU", "10", new BigDecimal("0.0123456789"), null), AHORA);
        assertThat(repo.crear(p)).isTrue();

        assertThat(repo.buscar(t, p.id()).orElseThrow().precioUnitario()).isEqualByComparingTo("0.0123456789");
    }

    @Test void elCodigoInternoEsUnicoPorEmpresaPeroLosSinCodigoNoChocan() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        producto(t, "SKU-1", "Servicio uno");

        assertThat(repo.crear(ProductoGuardado.nuevo(t, new Datos("SKU-1", "Otro", "ZZ", "10", BigDecimal.TEN, null), AHORA))).as("el código ya existe en la empresa: no escribe nada").isFalse();
        assertThat(repo.crear(ProductoGuardado.nuevo(otra, new Datos("SKU-1", "Otro", "ZZ", "10", BigDecimal.TEN, null), AHORA))).as("otra empresa puede repetirlo").isTrue();
        assertThat(repo.crear(ProductoGuardado.nuevo(t, new Datos(null, "Sin código A", "ZZ", "10", BigDecimal.TEN, null), AHORA))).isTrue();
        assertThat(repo.crear(ProductoGuardado.nuevo(t, new Datos(null, "Sin código B", "ZZ", "10", BigDecimal.TEN, null), AHORA))).as("sin código no hay unicidad").isTrue();
        assertThat(repo.crear(ProductoGuardado.nuevo(t, new Datos(null, "Sin código A", "ZZ", "10", BigDecimal.TEN, null), AHORA))).as("la descripción puede repetirse").isTrue();
    }

    @Test void actualizarCambiaLosDatosYAvisaSiElNuevoCodigoEsDeOtro() {
        UUID t = tenantDePrueba();
        ProductoGuardado a = producto(t, "SKU-1", "Servicio uno");
        producto(t, "SKU-2", "Servicio dos");

        ProductoGuardado cambiado = a.con(new Datos("SKU-1", "Servicio uno plus", "HUR", "20", new BigDecimal("50"), null), AHORA.plusSeconds(60));
        assertThat(repo.actualizar(cambiado)).isTrue();
        assertThat(repo.buscar(t, a.id())).contains(cambiado);
        ProductoGuardado choca = a.con(new Datos("SKU-2", "Servicio uno", "ZZ", "10", BigDecimal.TEN, null), AHORA.plusSeconds(120));
        assertThat(repo.actualizar(choca)).as("el código ya es de otro producto").isFalse();
        assertThat(repo.buscar(t, a.id()).orElseThrow().codigo()).isEqualTo("SKU-1");
    }

    @Test void unProductoDeOtraEmpresaNoSeVeNiSeCambiaNiSeBorra() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        ProductoGuardado p = producto(t, "SKU-1", "Servicio uno");

        assertThat(repo.buscar(otra, p.id())).isEmpty();
        ProductoGuardado ajeno = new ProductoGuardado(p.id(), otra, "SKU-1", "ROBADO", "ZZ", "10", BigDecimal.TEN, null, AHORA, AHORA);
        assertThat(repo.actualizar(ajeno)).as("con el tenant equivocado no toca nada").isFalse();
        assertThat(repo.eliminar(otra, p.id())).isFalse();
        assertThat(repo.listar(otra, null, 1, 20)).isEmpty();
        assertThat(repo.contar(otra, null)).isZero();
        assertThat(repo.buscar(t, p.id()).orElseThrow().descripcion()).isEqualTo("Servicio uno");
        assertThat(repo.eliminar(t, p.id())).isTrue();
        assertThat(repo.eliminar(t, p.id())).isFalse();
    }

    @Test void listaPorDescripcionYPaginaYUnaPaginaMuyLejanaVieneVacia() {
        UUID t = tenantDePrueba();
        for (String n : new String[]{"Zeta", "Alfa", "Beta", "Gama"}) producto(t, null, n);

        assertThat(repo.listar(t, null, 1, 3)).extracting(ProductoGuardado::descripcion).containsExactly("Alfa", "Beta", "Gama");
        assertThat(repo.listar(t, null, 2, 3)).extracting(ProductoGuardado::descripcion).containsExactly("Zeta");
        assertThat(repo.contar(t, null)).isEqualTo(4);
        assertThat(repo.listar(t, null, Integer.MAX_VALUE, 100)).isEmpty();
    }

    @Test void buscaPorElComienzoDelCodigoYPorCualquierParteDeLaDescripcionSinMayusculasNiTildes() {
        UUID t = tenantDePrueba();
        producto(t, "SKU-100", "Consultoría tributaria");
        producto(t, "ABC-1", "Auditoría SKU externa");
        producto(t, null, "Capacitación 100% en línea");

        assertThat(repo.listar(t, "consultoria", 1, 20)).extracting(ProductoGuardado::codigo).containsExactly("SKU-100");
        assertThat(repo.listar(t, "AUDITORIA", 1, 20)).extracting(ProductoGuardado::codigo).containsExactly("ABC-1");
        assertThat(repo.listar(t, "sku", 1, 20)).as("código que empieza por sku o descripción que lo contiene").extracting(ProductoGuardado::codigo).containsExactlyInAnyOrder("SKU-100", "ABC-1");
        assertThat(repo.listar(t, "-100", 1, 20)).as("un código no se busca por el medio").isEmpty();
        assertThat(repo.contar(t, "sku")).isEqualTo(2);
        assertThat(repo.listar(t, "%", 1, 20)).as("los comodines de LIKE son texto").extracting(ProductoGuardado::descripcion).containsExactly("Capacitación 100% en línea");
        assertThat(repo.listar(t, "_", 1, 20)).isEmpty();
    }
}
