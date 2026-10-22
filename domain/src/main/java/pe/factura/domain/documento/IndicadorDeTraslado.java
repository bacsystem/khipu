package pe.factura.domain.documento;

import lombok.Getter;
import pe.factura.domain.DomainException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Indicadores especiales del traslado ({@code cac:Shipment/cbc:SpecialInstructions}). El valor del XML es el nombre que usan SUNAT y las implementaciones
 * conocidas de la GRE 2022; el repositorio no tiene la guía de elaboración del XML de la GRE, así que es un supuesto sin verificar contra e-beta. Las
 * combinaciones que prohíben las reglas 3450 y 3451 se validan en {@link GuiaRemision}.
 */
public enum IndicadorDeTraslado {
    /** El traslado se hace en vehículos de categoría M1 o L (autos, motos): no hace falta registrar vehículo ni conductor (regla 3451). */
    VEHICULO_M1_O_L("vehiculo_m1_o_l", "SUNAT_Envio_IndicadorTrasladoVehiculoM1L"),
    RETORNO_VEHICULO_ENVASE_VACIO("retorno_vehiculo_envase_vacio", "SUNAT_Envio_IndicadorRetornoVehiculoEnvaseVacio"),
    RETORNO_VEHICULO_VACIO("retorno_vehiculo_vacio", "SUNAT_Envio_IndicadorRetornoVehiculoVacio"),
    TRANSBORDO_PROGRAMADO("transbordo_programado", "SUNAT_Envio_IndicadorTransbordoProgramado"),
    /** Transporte público en el que el remitente registra los vehículos y conductores del transportista (reglas 3450 y 3451). */
    VEHICULOS_CONDUCTORES_TRANSPORTISTA("vehiculos_conductores_transportista", "SUNAT_Envio_IndicadorVehiculoConductoresTransp");

    /** Cómo se escribe en la API de khipu. */
    @Getter private final String codigo;
    /** Cómo va en el XML. */
    @Getter private final String valorXml;

    IndicadorDeTraslado(String codigo, String valorXml) { this.codigo = codigo; this.valorXml = valorXml; }

    /** Los que admite cada tipo de guía: los de vehículos M1/L y de vehículos del transportista solo tienen sentido en la remitente, que elige la modalidad. */
    static Set<IndicadorDeTraslado> admitidos(TipoDocumento tipo) {
        return tipo == TipoDocumento.GUIA_REMITENTE ? EnumSet.allOf(IndicadorDeTraslado.class)
                : EnumSet.of(RETORNO_VEHICULO_ENVASE_VACIO, RETORNO_VEHICULO_VACIO, TRANSBORDO_PROGRAMADO);
    }

    public static IndicadorDeTraslado porCodigo(String codigo) {
        for (IndicadorDeTraslado i : values()) if (i.codigo.equals(codigo) || i.valorXml.equals(codigo)) return i;
        throw new DomainException("TRASLADO_INVALIDO", "Indicador de traslado desconocido: " + codigo + " (vehiculo_m1_o_l, retorno_vehiculo_envase_vacio, retorno_vehiculo_vacio, "
                + "transbordo_programado o vehiculos_conductores_transportista)");
    }
}
