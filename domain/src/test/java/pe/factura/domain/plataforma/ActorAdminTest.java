package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorAdminTest {
    @Test void administradorConservaSuIdYLaIp() {
        UUID id = UUID.randomUUID();
        ActorAdmin actor = ActorAdmin.administrador(id, "203.0.113.7");
        assertThat(actor.tipo()).isEqualTo(ActorAdmin.Tipo.ADMINISTRADOR);
        assertThat(actor.administradorId()).isEqualTo(id);
        assertThat(actor.ip()).isEqualTo("203.0.113.7");
    }

    @Test void laClaveDePlataformaNoTieneAdministrador() {
        ActorAdmin actor = ActorAdmin.clavePlataforma("203.0.113.7");
        assertThat(actor.tipo()).isEqualTo(ActorAdmin.Tipo.CLAVE_PLATAFORMA);
        assertThat(actor.administradorId()).isNull();
    }

    @Test void laClaveDePlataformaNoPuedeAtribuirseAUnAdministrador() {
        // La fábrica no lo permite, pero el constructor del record es público: la invariante debe vivir en el record.
        assertThatThrownBy(() -> new ActorAdmin(ActorAdmin.Tipo.CLAVE_PLATAFORMA, UUID.randomUUID(), "203.0.113.7"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ACTOR_INVALIDO");
    }

    @Test void unAdministradorSinIdNoEsUnActorValido() {
        assertThatThrownBy(() -> ActorAdmin.administrador(null, "203.0.113.7"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ACTOR_INVALIDO");
    }

    /**
     * La misma IPv6 llega con grafías distintas según la ruta: la JVM la escribe sin comprimir (lo que Tomcat da para una conexión
     * directa) y el portal reenvía la forma canónica comprimida. Sin una forma única, `WHERE ip = '2001:db8::1'` se perdería las filas
     * escritas por llamadas directas. Se guarda siempre en la forma de la JVM (#208).
     */
    @Test void unaIpv6SeGuardaSiempreEnLaFormaSinComprimir() {
        assertThat(ActorAdmin.clavePlataforma("2001:db8::1").ip()).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(ActorAdmin.clavePlataforma("::1").ip()).isEqualTo("0:0:0:0:0:0:0:1");
        assertThat(ActorAdmin.clavePlataforma("::").ip()).isEqualTo("0:0:0:0:0:0:0:0");
        assertThat(ActorAdmin.clavePlataforma("2001:db8:0:0:1::5").ip()).isEqualTo("2001:db8:0:0:1:0:0:5");
    }

    @Test void laMismaIpv6EnCualquierGrafiaQuedaIgual() {
        String esperada = "2001:db8:0:0:0:0:0:1";
        for (String grafia : new String[] {"2001:db8::1", "2001:DB8::1", "2001:0db8:0000:0000:0000:0000:0000:0001", "2001:db8:0:0:0:0:0:1", " 2001:db8::1 "}) {
            assertThat(ActorAdmin.clavePlataforma(grafia).ip()).as(grafia).isEqualTo(esperada);
        }
    }

    @Test void coincideConLaFormaQueDaLaJvmParaUnaConexionDirecta() throws Exception {
        // Oráculo: lo que `getRemoteAddr()` devuelve es `InetAddress#getHostAddress`. Solo con literales válidos, que no consultan DNS.
        for (String literal : new String[] {"2001:db8::1", "::1", "fe80::1", "2001:db8:abcd:12::", "1:2:3:4:5:6:7:8", "2001:db8:0:0:1::5", "ff02::1:ff00:1"}) {
            assertThat(ActorAdmin.normalizarIp(literal)).as(literal).isEqualTo(java.net.InetAddress.getByName(literal).getHostAddress());
        }
    }

    @Test void unaIpv4MapeadaEnIpv6EsLaIpv4() {
        // Como en la JVM: una conexión IPv4 por un socket de doble pila se ve como IPv4.
        for (String grafia : new String[] {"::ffff:203.0.113.7", "::FFFF:203.0.113.7", "0:0:0:0:0:ffff:203.0.113.7", "::ffff:cb00:7107"}) {
            assertThat(ActorAdmin.clavePlataforma(grafia).ip()).as(grafia).isEqualTo("203.0.113.7");
        }
    }

    @Test void unaIpv4NoCambia() {
        assertThat(ActorAdmin.clavePlataforma("203.0.113.7").ip()).isEqualTo("203.0.113.7");
    }

    @Test void loQueNoEsUnaIpv6ValidaNoSeTocaNiSeResuelveContraUnDns() {
        // Sin `:` no se interpreta; con `:` pero mal formada, tampoco: `InetAddress.getByName` consultaría el DNS con estos textos.
        for (String basura : new String[] {"desconocida", "localhost", "abc:def", "1:2:3:4:5:6:7:8:9", ":::", "12345::1", "2001:db8::1::2", "g::1", "1:2:3:4:5:6:7", "::1.2.3"}) {
            assertThat(ActorAdmin.normalizarIp(basura)).as(basura).isEqualTo(basura);
        }
    }

    @Test void normalizarEsIdempotente() {
        for (String ip : new String[] {"2001:db8::1", "::ffff:203.0.113.7", "203.0.113.7", "desconocida"}) {
            assertThat(ActorAdmin.normalizarIp(ActorAdmin.normalizarIp(ip))).as(ip).isEqualTo(ActorAdmin.normalizarIp(ip));
        }
    }

    @Test void sinIpNoHayAuditoriaDefendible() {
        assertThatThrownBy(() -> ActorAdmin.clavePlataforma(" "))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ACTOR_INVALIDO");
        assertThatThrownBy(() -> ActorAdmin.administrador(UUID.randomUUID(), null))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ACTOR_INVALIDO");
    }
}
