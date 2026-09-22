package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos sectoriales por ítem del servicio de transporte de carga con detracción (tipo de operación 1004, campos 113–127 de
 * la hoja Factura2_0): origen y destino con ubigeo (catálogo 13) y dirección, detalle del viaje, y los tres valores
 * referenciales en soles del D.S. 010-2006-MTC —del servicio (01), sobre la carga efectiva (02) y sobre la carga útil
 * nominal (03)— que SUNAT exige exactamente una vez cada uno (3116–3126, 3208). Los tramos y vehículos son opcionales
 * ({@code cac:Shipment/cac:Consignment}, solo observaciones 4200 y 4270–4278). Se valida solo al emitir
 * ({@link Detraccion#validarDatosSectoriales}), no al rehidratar (#89): un ubigeo del catálogo 13 puede reorganizarse
 * después de que SUNAT ya aceptó el comprobante, y eso no debe impedir volver a leerlo.
 */
public record TransporteCarga(Punto origen, Punto destino, String detalleViaje, ValorReferencial valorReferencial, List<Tramo> tramos) {

    /** Ubigeo del catálogo 13 (4200) y dirección de 3 a 200 caracteres (4236). */
    public record Punto(String ubigeo, String direccion) {
        public Punto {
            direccion = direccion == null ? null : direccion.strip();
        }
    }

    /** Montos en soles, positivos, hasta 12 enteros y 2 decimales (3122/3123). */
    public record ValorReferencial(BigDecimal servicio, BigDecimal cargaEfectiva, BigDecimal cargaUtilNominal) {}

    /** Tramo del viaje (campos 119–122 y 127): ubigeos de origen y destino, descripción y valores preliminares; todo opcional. */
    public record Tramo(String origenUbigeo, String destinoUbigeo, String descripcion, BigDecimal valorCargaEfectiva, BigDecimal valorCargaUtilNominal, List<Vehiculo> vehiculos) {
        public Tramo {
            descripcion = descripcion == null ? null : descripcion.strip();
            vehiculos = vehiculos == null ? List.of() : List.copyOf(vehiculos);
        }
    }

    /** Vehículo del tramo (campos 123–125): configuración vehicular (D.S. 058-2003-MTC) y cargas útil/efectiva en TM. */
    public record Vehiculo(String configuracion, BigDecimal cargaUtilTm, BigDecimal cargaEfectivaTm) {}

    public TransporteCarga {
        detalleViaje = detalleViaje == null ? null : detalleViaje.strip();
        tramos = tramos == null ? List.of() : List.copyOf(tramos);
    }

    /**
     * Reglas del transporte de carga (3116–3126, 3208; tramos y vehículos 4200, 4270–4278) — antes de numerar. No se llama
     * al rehidratar: un comprobante ya emitido, aunque un ubigeo deje de existir en el catálogo después, se sigue leyendo.
     */
    void exigirValido() {
        if (origen == null) throw new DomainException("DETRACCION_INVALIDA", "3116 - transporte.origen (ubigeo y dirección) es obligatorio en el transporte de carga");
        exigirPunto(origen, "origen");
        if (destino == null) throw new DomainException("DETRACCION_INVALIDA", "3118 - transporte.destino (ubigeo y dirección) es obligatorio en el transporte de carga");
        exigirPunto(destino, "destino");
        if (detalleViaje == null || detalleViaje.length() < 3 || detalleViaje.length() > 500 || detalleViaje.chars().anyMatch(Character::isISOControl))
            throw new DomainException("DETRACCION_INVALIDA", "3120 - transporte.detalle_viaje es obligatorio: de 3 a 500 caracteres sin saltos de línea (4270)");
        if (valorReferencial == null)
            throw new DomainException("DETRACCION_INVALIDA", "3122 - transporte.valor_referencial (servicio, carga_efectiva y carga_util_nominal, en soles) es obligatorio");
        exigirMonto(valorReferencial.servicio(), "valor_referencial.servicio", "3124");
        exigirMonto(valorReferencial.cargaEfectiva(), "valor_referencial.carga_efectiva", "3125");
        exigirMonto(valorReferencial.cargaUtilNominal(), "valor_referencial.carga_util_nominal", "3126");
        tramos.forEach(TransporteCarga::exigirTramo);
    }

    private static void exigirPunto(Punto p, String campo) {
        if (!existeUbigeo(p.ubigeo()))
            throw new DomainException("DETRACCION_INVALIDA", "3116 - El ubigeo del punto de " + campo + " debe existir en el catálogo 13: " + p.ubigeo());
        if (p.direccion() == null || p.direccion().length() < 3 || p.direccion().length() > 200 || p.direccion().chars().anyMatch(Character::isISOControl))
            throw new DomainException("DETRACCION_INVALIDA", "3117 - La dirección del punto de " + campo + " tiene de 3 a 200 caracteres sin saltos de línea");
    }

    private static void exigirTramo(Tramo t) {
        if (t.origenUbigeo() != null && !existeUbigeo(t.origenUbigeo()))
            throw new DomainException("DETRACCION_INVALIDA", "4200 - El ubigeo de origen del tramo debe existir en el catálogo 13: " + t.origenUbigeo());
        if (t.destinoUbigeo() != null && !existeUbigeo(t.destinoUbigeo()))
            throw new DomainException("DETRACCION_INVALIDA", "4200 - El ubigeo de destino del tramo debe existir en el catálogo 13: " + t.destinoUbigeo());
        if (t.descripcion() != null && (t.descripcion().length() < 3 || t.descripcion().length() > 100 || t.descripcion().chars().anyMatch(Character::isISOControl)))
            throw new DomainException("DETRACCION_INVALIDA", "4271 - La descripción del tramo tiene de 3 a 100 caracteres sin saltos de línea");
        if (t.valorCargaEfectiva() != null) exigirMonto(t.valorCargaEfectiva(), "tramos[].valor_carga_efectiva", "4272");
        if (t.valorCargaUtilNominal() != null) exigirMonto(t.valorCargaUtilNominal(), "tramos[].valor_carga_util_nominal", "4278");
        t.vehiculos().forEach(TransporteCarga::exigirVehiculo);
    }

    private static void exigirVehiculo(Vehiculo v) {
        if (v.configuracion() != null && (v.configuracion().isBlank() || v.configuracion().length() > 15 || v.configuracion().chars().anyMatch(Character::isWhitespace)))
            throw new DomainException("DETRACCION_INVALIDA", "4273 - La configuración vehicular tiene de 1 a 15 caracteres sin espacios");
        if (v.cargaUtilTm() != null) exigirMonto(v.cargaUtilTm(), "vehiculos[].carga_util_tm", "4276");
        if (v.cargaEfectivaTm() != null) exigirMonto(v.cargaEfectivaTm(), "vehiculos[].carga_efectiva_tm", "4276");
    }

    private static void exigirMonto(BigDecimal v, String campo, String regla) {
        if (v == null || v.signum() <= 0 || v.scale() > 2 || v.precision() - v.scale() > 12)
            throw new DomainException("DETRACCION_INVALIDA", regla + " - transporte." + campo + " debe ser un monto positivo en soles con hasta 12 enteros y 2 decimales");
    }

    /** Catálogo 13 (UBIGEO, INEI): único punto de acceso para no repetir la búsqueda por cada punto/tramo. */
    private static boolean existeUbigeo(String ubigeo) {
        return ubigeo != null && CatalogoSunat.porId("13").orElseThrow().contiene(ubigeo);
    }
}
