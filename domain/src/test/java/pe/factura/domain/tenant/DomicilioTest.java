package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Domicilio fiscal del emisor: reglas 4093–4098 (RegistrationAddress) y 3030 (establecimiento anexo) de la hoja Factura2_0. */
class DomicilioTest {

    @Test void completaDistritoProvinciaYDepartamentoDesdeElUbigeo() {
        Domicilio d = Domicilio.de("150122", "  Av. Larco 345 Of. 12  ");
        assertThat(d.direccion()).isEqualTo("Av. Larco 345 Of. 12");
        assertThat(d.distrito()).isEqualTo("MIRAFLORES");
        assertThat(d.provincia()).isEqualTo("LIMA");
        assertThat(d.departamento()).isEqualTo("LIMA");
        assertThat(d.codigoEstablecimiento()).isEqualTo("0000");
        assertThat(d.urbanizacion()).isNull();
        assertThat(CatalogoSunat.porId("13").orElseThrow().entradas()).hasSize(1892);
    }

    @Test void respetaLosNombresYElEstablecimientoIndicados() {
        Domicilio d = new Domicilio("040101", "Calle Mercaderes 100", "Urb. Centro", "Arequipa", "Arequipa", "Arequipa", "0001");
        assertThat(d.distrito()).isEqualTo("Arequipa");
        assertThat(d.urbanizacion()).isEqualTo("Urb. Centro");
        assertThat(d.codigoEstablecimiento()).isEqualTo("0001");
    }

    @Test void rechazaUbigeoDireccionYEstablecimientoInvalidos() {
        assertThatThrownBy(() -> Domicilio.de("999999", "Av. Larco 345")).isInstanceOf(DomainException.class).hasMessageStartingWith("4093");
        assertThatThrownBy(() -> Domicilio.de("15012", "Av. Larco 345")).hasMessageStartingWith("4093");
        assertThatThrownBy(() -> Domicilio.de("150122", "Av")).hasMessageStartingWith("4094");
        assertThatThrownBy(() -> Domicilio.de("150122", "Av. Larco\n345")).hasMessageStartingWith("4094");
        assertThatThrownBy(() -> Domicilio.de("150122", "Av. Larco\n345\nOf. 2")).hasMessageStartingWith("4094");   // dos saltos: '.' no cruza líneas en una regex
        assertThatThrownBy(() -> Domicilio.de("150122", "Av. Larco\r\n345")).hasMessageStartingWith("4094");
        assertThatThrownBy(() -> new Domicilio("150122", "Av. Larco 345", "Urb.\tA\tB", null, null, null, null)).hasMessageStartingWith("4095");
        assertThatThrownBy(() -> Domicilio.de("150122", "A".repeat(201))).hasMessageStartingWith("4094");
        assertThatThrownBy(() -> new Domicilio("150122", "Av. Larco 345", "U".repeat(26), null, null, null, null)).hasMessageStartingWith("4095");
        assertThatThrownBy(() -> new Domicilio("150122", "Av. Larco 345", null, "D".repeat(31), null, null, null)).hasMessageStartingWith("4098");
        assertThatThrownBy(() -> new Domicilio("150122", "Av. Larco 345", null, null, null, null, "12")).hasMessageStartingWith("3030");
    }

    @Test void cuentaDeDetraccionesDelTenant() {
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA, null, null).conDatosFiscales(Domicilio.de("150122", "Av. Larco 345"), " 00-000-123456 ");
        assertThat(t.cuentaDetracciones()).isEqualTo("00-000-123456");
        assertThat(t.domicilio().distrito()).isEqualTo("MIRAFLORES");
        assertThat(t.conDatosFiscales(null, "").domicilio()).isNull();
        assertThat(t.conDatosFiscales(null, "").cuentaDetracciones()).isNull();
        assertThatThrownBy(() -> t.conDatosFiscales(null, "cuenta"))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CUENTA_DETRACCIONES_INVALIDA");
    }
}
