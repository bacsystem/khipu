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
 * ({@code cac:Shipment/cac:Consignment}, solo observaciones 4200 y 4270–4278).
 */
public record TransporteCarga(Punto origen, Punto destino, String detalleViaje, ValorReferencial valorReferencial, List<Tramo> tramos) {

    /** Ubigeo del catálogo 13 (4200) y dirección de 3 a 200 caracteres (4236). */
    public record Punto(String ubigeo, String direccion) {
        public Punto {
            if (ubigeo == null || !CatalogoSunat.porId("13").orElseThrow().contiene(ubigeo))
                throw new DomainException("DETRACCION_INVALIDA", "3116 - El ubigeo del punto de origen/destino debe existir en el catálogo 13: " + ubigeo);
            if (direccion == null || direccion.strip().length() < 3 || direccion.strip().length() > 200 || direccion.chars().anyMatch(Character::isISOControl))
                throw new DomainException("DETRACCION_INVALIDA", "3117 - La dirección del punto de origen/destino tiene de 3 a 200 caracteres sin saltos de línea");
            direccion = direccion.strip();
        }
    }

    /** Montos en soles, positivos, hasta 12 enteros y 2 decimales (3122/3123). */
    public record ValorReferencial(BigDecimal servicio, BigDecimal cargaEfectiva, BigDecimal cargaUtilNominal) {
        public ValorReferencial {
            servicio = monto(servicio, "servicio", "3124");
            cargaEfectiva = monto(cargaEfectiva, "carga_efectiva", "3125");
            cargaUtilNominal = monto(cargaUtilNominal, "carga_util_nominal", "3126");
        }
    }

    /** Tramo del viaje (campos 119–122 y 127): ubigeos de origen y destino, descripción y valores preliminares; todo opcional. */
    public record Tramo(String origenUbigeo, String destinoUbigeo, String descripcion, BigDecimal valorCargaEfectiva, BigDecimal valorCargaUtilNominal, List<Vehiculo> vehiculos) {
        public Tramo {
            if (origenUbigeo != null && !CatalogoSunat.porId("13").orElseThrow().contiene(origenUbigeo))
                throw new DomainException("DETRACCION_INVALIDA", "4200 - El ubigeo de origen del tramo debe existir en el catálogo 13: " + origenUbigeo);
            if (destinoUbigeo != null && !CatalogoSunat.porId("13").orElseThrow().contiene(destinoUbigeo))
                throw new DomainException("DETRACCION_INVALIDA", "4200 - El ubigeo de destino del tramo debe existir en el catálogo 13: " + destinoUbigeo);
            if (descripcion != null && (descripcion.strip().length() < 3 || descripcion.strip().length() > 100 || descripcion.chars().anyMatch(Character::isISOControl)))
                throw new DomainException("DETRACCION_INVALIDA", "4271 - La descripción del tramo tiene de 3 a 100 caracteres sin saltos de línea");
            descripcion = descripcion == null ? null : descripcion.strip();
            if (valorCargaEfectiva != null) valorCargaEfectiva = monto(valorCargaEfectiva, "tramos[].valor_carga_efectiva", "4272");
            if (valorCargaUtilNominal != null) valorCargaUtilNominal = monto(valorCargaUtilNominal, "tramos[].valor_carga_util_nominal", "4278");
            vehiculos = vehiculos == null ? List.of() : List.copyOf(vehiculos);
        }
    }

    /** Vehículo del tramo (campos 123–125): configuración vehicular (D.S. 058-2003-MTC) y cargas útil/efectiva en TM. */
    public record Vehiculo(String configuracion, BigDecimal cargaUtilTm, BigDecimal cargaEfectivaTm) {
        public Vehiculo {
            if (configuracion != null && (configuracion.isBlank() || configuracion.length() > 15 || configuracion.chars().anyMatch(Character::isWhitespace)))
                throw new DomainException("DETRACCION_INVALIDA", "4273 - La configuración vehicular tiene de 1 a 15 caracteres sin espacios");
            if (cargaUtilTm != null) cargaUtilTm = monto(cargaUtilTm, "vehiculos[].carga_util_tm", "4276");
            if (cargaEfectivaTm != null) cargaEfectivaTm = monto(cargaEfectivaTm, "vehiculos[].carga_efectiva_tm", "4276");
        }
    }

    public TransporteCarga {
        if (origen == null) throw new DomainException("DETRACCION_INVALIDA", "3116 - transporte.origen (ubigeo y dirección) es obligatorio en el transporte de carga");
        if (destino == null) throw new DomainException("DETRACCION_INVALIDA", "3118 - transporte.destino (ubigeo y dirección) es obligatorio en el transporte de carga");
        if (detalleViaje == null || detalleViaje.strip().length() < 3 || detalleViaje.strip().length() > 500 || detalleViaje.chars().anyMatch(Character::isISOControl))
            throw new DomainException("DETRACCION_INVALIDA", "3120 - transporte.detalle_viaje es obligatorio: de 3 a 500 caracteres sin saltos de línea (4270)");
        detalleViaje = detalleViaje.strip();
        if (valorReferencial == null)
            throw new DomainException("DETRACCION_INVALIDA", "3122 - transporte.valor_referencial (servicio, carga_efectiva y carga_util_nominal, en soles) es obligatorio");
        tramos = tramos == null ? List.of() : List.copyOf(tramos);
    }

    private static BigDecimal monto(BigDecimal v, String campo, String regla) {
        if (v == null || v.signum() <= 0 || v.scale() > 2 || v.precision() - v.scale() > 12)
            throw new DomainException("DETRACCION_INVALIDA", regla + " - transporte." + campo + " debe ser un monto positivo en soles con hasta 12 enteros y 2 decimales");
        return v.setScale(2);
    }
}
