package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.ClienteGuardado;
import pe.factura.domain.documento.ClienteGuardado.Datos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Los clientes guardados (#319) con Postgres real: clave por empresa, búsqueda por documento y nombre, y aislamiento entre empresas. */
class JdbcClienteRepositoryTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");

    JdbcClienteRepository repo = new JdbcClienteRepository(jdbc);

    ClienteGuardado cliente(UUID tenant, String tipo, String num, String nombre) {
        ClienteGuardado c = ClienteGuardado.nuevo(tenant, new Datos(tipo, num, nombre, "Av. Lima 123", "PE", "a@b.pe"), AHORA);
        assertThat(repo.crear(c)).isTrue();
        return c;
    }

    @Test void guardaYLeeTodosLosCampos() {
        UUID t = tenantDePrueba();
        ClienteGuardado c = cliente(t, "6", "20601234565", "COMERCIAL ANDINA S.A.C.");

        assertThat(repo.buscar(t, c.id())).contains(c);
        assertThat(repo.buscarPorDocumento(t, "6", "20601234565")).contains(c);
        assertThat(repo.buscarPorDocumento(t, "1", "20601234565")).as("el tipo forma parte de la clave").isEmpty();
    }

    @Test void elDocumentoEsUnicoPorEmpresaPeroOtraEmpresaPuedeTenerElMismo() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        cliente(t, "6", "20601234565", "COMERCIAL ANDINA S.A.C.");

        ClienteGuardado repetido = ClienteGuardado.nuevo(t, new Datos("6", "20601234565", "OTRO NOMBRE", null, null, null), AHORA);

        assertThat(repo.crear(repetido)).as("ya existe en la empresa: no escribe nada").isFalse();
        assertThat(repo.buscarPorDocumento(t, "6", "20601234565").orElseThrow().razonSocial()).isEqualTo("COMERCIAL ANDINA S.A.C.");
        assertThat(repo.crear(ClienteGuardado.nuevo(otra, new Datos("6", "20601234565", "COMERCIAL ANDINA S.A.C.", null, null, null), AHORA))).isTrue();
    }

    @Test void actualizarCambiaLosDatosYAvisaSiElNuevoDocumentoEsDeOtro() {
        UUID t = tenantDePrueba();
        ClienteGuardado a = cliente(t, "1", "12345678", "JUAN PEREZ");
        cliente(t, "1", "87654321", "MARIA LOPEZ");

        ClienteGuardado cambiado = a.con(new Datos("1", "12345678", "JUAN PEREZ GOMEZ", null, null, null), AHORA.plusSeconds(60));
        assertThat(repo.actualizar(cambiado)).isTrue();
        assertThat(repo.buscar(t, a.id())).contains(cambiado);
        assertThat(repo.buscar(t, a.id()).orElseThrow().direccion()).as("lo que ya no se informa se borra").isNull();

        ClienteGuardado choca = a.con(new Datos("1", "87654321", "JUAN PEREZ GOMEZ", null, null, null), AHORA.plusSeconds(120));
        assertThat(repo.actualizar(choca)).as("el documento ya es de María").isFalse();
        assertThat(repo.buscar(t, a.id()).orElseThrow().numDoc()).isEqualTo("12345678");
    }

    @Test void unClienteDeOtraEmpresaNoSeVeNiSeCambiaNiSeBorra() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        ClienteGuardado c = cliente(t, "1", "12345678", "JUAN PEREZ");

        assertThat(repo.buscar(otra, c.id())).isEmpty();
        assertThat(repo.actualizar(c.con(new Datos("1", "12345678", "HACKEADO", null, null, null), AHORA))).isTrue();   // su dueña sí
        ClienteGuardado ajeno = new ClienteGuardado(c.id(), otra, "1", "12345678", "ROBADO", null, null, null, AHORA, AHORA);
        assertThat(repo.actualizar(ajeno)).as("con el tenant equivocado no toca nada").isFalse();
        assertThat(repo.eliminar(otra, c.id())).isFalse();
        assertThat(repo.listar(otra, null, 1, 20)).isEmpty();
        assertThat(repo.contar(otra, null)).isZero();
        assertThat(repo.buscarPorDocumento(otra, "1", "12345678")).as("el mismo documento en otra empresa no es este cliente").isEmpty();
        assertThat(repo.buscar(t, c.id()).orElseThrow().razonSocial()).isEqualTo("HACKEADO");
        assertThat(repo.eliminar(t, c.id())).isTrue();
        assertThat(repo.eliminar(t, c.id())).isFalse();
    }

    @Test void listaPorNombreYPagina() {
        UUID t = tenantDePrueba();
        for (String n : List.of("ZETA SAC", "ALFA SAC", "BETA SAC", "GAMA SAC")) cliente(t, "1", String.format("%08d", Math.abs(n.hashCode()) % 100000000), n);

        assertThat(repo.listar(t, null, 1, 3)).extracting(ClienteGuardado::razonSocial).containsExactly("ALFA SAC", "BETA SAC", "GAMA SAC");
        assertThat(repo.listar(t, null, 2, 3)).extracting(ClienteGuardado::razonSocial).containsExactly("ZETA SAC");
        assertThat(repo.contar(t, null)).isEqualTo(4);
    }

    @Test void unaPaginaMuyLejanaDevuelveVaciaEnVezDeFallarPorDesbordarElOffset() {
        UUID t = tenantDePrueba();
        cliente(t, "1", "12345678", "JUAN PEREZ");

        assertThat(repo.listar(t, null, Integer.MAX_VALUE, 100)).isEmpty();
    }

    @Test void buscaPorElComienzoDelDocumentoYPorCualquierParteDelNombreSinMayusculasNiTildes() {
        UUID t = tenantDePrueba();
        cliente(t, "6", "20601234565", "PESQUERA PACÍFICO S.A.C.");
        cliente(t, "1", "20111111", "JUAN PÉREZ");
        cliente(t, "1", "30222222", "MARÍA 20 LOPEZ");

        assertThat(repo.listar(t, "pacifico", 1, 20)).extracting(ClienteGuardado::razonSocial).containsExactly("PESQUERA PACÍFICO S.A.C.");
        assertThat(repo.listar(t, "PÉREZ", 1, 20)).extracting(ClienteGuardado::razonSocial).containsExactly("JUAN PÉREZ");
        assertThat(repo.listar(t, "maria", 1, 20)).extracting(ClienteGuardado::razonSocial).containsExactly("MARÍA 20 LOPEZ");
        assertThat(repo.listar(t, "2060", 1, 20)).as("el documento empieza por").extracting(ClienteGuardado::numDoc).containsExactly("20601234565");
        assertThat(repo.listar(t, "20", 1, 20)).as("documento que empieza por 20 o nombre que contiene 20").extracting(ClienteGuardado::numDoc).containsExactlyInAnyOrder("20601234565", "20111111", "30222222");
        assertThat(repo.listar(t, "1234", 1, 20)).as("un documento no se busca por el medio").isEmpty();
        assertThat(repo.contar(t, "pacifico")).isEqualTo(1);
    }

    @Test void losComodinesDeLikeSeTomanLiteralmente() {
        UUID t = tenantDePrueba();
        cliente(t, "1", "11111111", "COMERCIAL 100% SAC");
        cliente(t, "1", "22222222", "FERRETERIA UNO");

        assertThat(repo.listar(t, "%", 1, 20)).extracting(ClienteGuardado::razonSocial).containsExactly("COMERCIAL 100% SAC");
        assertThat(repo.listar(t, "_", 1, 20)).isEmpty();
        assertThat(repo.contar(t, "%")).isEqualTo(1);
    }
}
