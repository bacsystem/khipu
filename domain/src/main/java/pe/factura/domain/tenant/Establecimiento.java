package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;

import java.util.UUID;

/**
 * Establecimiento anexo del emisor (sucursal, tienda, almacén) declarado en su ficha RUC: código de 4 dígitos (regla 3030),
 * nombre propio y domicilio. Cada serie se asigna a uno y el comprobante sale con su {@code cac:RegistrationAddress}.
 * El domicilio fiscal ({@code 0000}) no se registra aquí: es {@link Tenant#domicilio()}, la única fuente de ese dato.
 */
public record Establecimiento(UUID tenantId, String codigo, String nombre, Domicilio domicilio, boolean activo) {

    public Establecimiento {
        if (tenantId == null) throw new DomainException("ESTABLECIMIENTO_INVALIDO", "Establecimiento sin empresa");
        codigo = codigo == null ? "" : codigo.strip();
        if (!codigo.matches("\\d{4}"))
            throw new DomainException("ESTABLECIMIENTO_INVALIDO", "3030 - El código del establecimiento anexo son 4 dígitos, tal como figura en la ficha RUC");
        if (Domicilio.ESTABLECIMIENTO_PRINCIPAL.equals(codigo))
            throw new DomainException("ESTABLECIMIENTO_INVALIDO", "El 0000 es el domicilio fiscal: se configura en los datos fiscales de la empresa, no como anexo");
        nombre = nombre == null ? "" : nombre.strip();
        if (nombre.isEmpty() || nombre.length() > 100 || nombre.chars().anyMatch(Character::isISOControl))
            throw new DomainException("ESTABLECIMIENTO_INVALIDO", "El nombre del establecimiento tiene de 1 a 100 caracteres, sin saltos de línea");
        if (domicilio == null) throw new DomainException("ESTABLECIMIENTO_INVALIDO", "El establecimiento necesita su domicilio (ubigeo y dirección)");
        // El AddressTypeCode del XML sale del domicilio: se fuerza al código del establecimiento para que no puedan discrepar.
        domicilio = new Domicilio(domicilio.ubigeo(), domicilio.direccion(), domicilio.urbanizacion(), domicilio.distrito(), domicilio.provincia(), domicilio.departamento(), codigo);
    }

    public Establecimiento desactivar() { return new Establecimiento(tenantId, codigo, nombre, domicilio, false); }
    public Establecimiento con(String nombre, Domicilio domicilio, boolean activo) { return new Establecimiento(tenantId, codigo, nombre, domicilio, activo); }
}
