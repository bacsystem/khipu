package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Datos del envío comunes a las dos guías ({@code cac:Shipment}): fecha de inicio del traslado, peso bruto total y su unidad, bultos, puntos de partida
 * y de llegada, vehículos y conductores (el primero de cada lista es el principal) e indicadores especiales. Las reglas que dependen del tipo de guía y
 * de la modalidad están en {@link GuiaRemision}.
 */
public record Traslado(LocalDate fechaInicio, BigDecimal pesoBruto, String unidadPeso, Integer bultos, PuntoDeTraslado partida, PuntoDeTraslado llegada,
                       List<Vehiculo> vehiculos, List<Conductor> conductores, Set<IndicadorDeTraslado> indicadores) {
    /** Unidades del peso bruto total: kilogramos o toneladas (regla 2881; el catálogo 03 tiene más, la GRE solo estas dos). */
    static final Set<String> UNIDADES_DE_PESO = Set.of("KGM", "TNE");
    static final int MAXIMO_VEHICULOS = 3, MAXIMO_CONDUCTORES = 3;

    public Traslado {
        unidadPeso = unidadPeso == null || unidadPeso.isBlank() ? "KGM" : unidadPeso.strip().toUpperCase(java.util.Locale.ROOT);
        vehiculos = vehiculos == null ? List.of() : List.copyOf(vehiculos);
        conductores = conductores == null ? List.of() : List.copyOf(conductores);
        indicadores = indicadores == null || indicadores.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(indicadores));
    }

    public boolean tiene(IndicadorDeTraslado i) { return indicadores.contains(i); }

    public boolean conVehiculos() { return !vehiculos.isEmpty(); }
    public boolean conConductores() { return !conductores.isEmpty(); }

    /** Lo que no depende del tipo de guía. */
    void exigirValido(LocalDate fechaEmision, TipoDocumento guia) {
        if (fechaInicio == null) throw new DomainException("TRASLADO_INVALIDO", "3406 - Falta la fecha de inicio del traslado");
        if (fechaInicio.isBefore(fechaEmision))
            throw new DomainException("TRASLADO_INVALIDO", "3343 - La fecha de inicio del traslado (" + fechaInicio + ") no puede ser anterior a la fecha de emisión (" + fechaEmision + ")");
        if (pesoBruto == null) throw new DomainException("TRASLADO_INVALIDO", "2880 - Falta el peso bruto total de los bienes");
        if (!TextoDeGuia.positivo(pesoBruto, 12, 3)) throw new DomainException("TRASLADO_INVALIDO", "2880 - El peso bruto total es mayor que cero, con hasta 12 enteros y 3 decimales");
        if (!UNIDADES_DE_PESO.contains(unidadPeso)) throw new DomainException("TRASLADO_INVALIDO", "2881 - La unidad del peso bruto total es KGM (kilogramos) o TNE (toneladas); recibido " + unidadPeso);
        // 4195: los bultos o pallets solo se informan en una importación (motivo 08), que khipu aún no emite (requiere la DAM y sus reglas aduaneras).
        if (bultos != null)
            throw new DomainException("TRASLADO_INVALIDO", "4195 - El número de bultos o pallets solo se informa en una importación (motivo 08), que khipu todavía no emite");
        if (partida == null) throw new DomainException("TRASLADO_INVALIDO", "1075 - Falta el punto de partida");
        if (llegada == null) throw new DomainException("TRASLADO_INVALIDO", "1074 - Falta el punto de llegada");
        partida.exigirValido(true);
        llegada.exigirValido(false);
        if (vehiculos.size() > MAXIMO_VEHICULOS) throw new DomainException("TRANSPORTE_INVALIDO", "4389 - Un vehículo principal y hasta dos secundarios (" + vehiculos.size() + " recibidos)");
        vehiculos.forEach(Vehiculo::exigirValido);
        if (vehiculos.stream().map(Vehiculo::placa).distinct().count() < vehiculos.size())
            throw new DomainException("TRANSPORTE_INVALIDO", "La misma placa aparece dos veces entre los vehículos");
        if (conductores.size() > MAXIMO_CONDUCTORES) throw new DomainException("TRANSPORTE_INVALIDO", "4376 - Un conductor principal y hasta dos secundarios (" + conductores.size() + " recibidos)");
        for (int i = 0; i < conductores.size(); i++) conductores.get(i).exigirValido(i);
        for (int i = 0; i < conductores.size(); i++)
            for (int j = i + 1; j < conductores.size(); j++)
                if (conductores.get(i).mismaPersonaQue(conductores.get(j)))
                    throw new DomainException("TRANSPORTE_INVALIDO", "3362 - El conductor " + conductores.get(j).numDoc() + " está repetido");
        for (IndicadorDeTraslado i : indicadores)
            if (!IndicadorDeTraslado.admitidos(guia).contains(i))
                throw new DomainException("TRASLADO_INVALIDO", "El indicador " + i.codigo() + " no va en una guía " + (guia == TipoDocumento.GUIA_REMITENTE ? "remitente" : "transportista"));
    }

    Traslado conPuntos(PuntoDeTraslado partida, PuntoDeTraslado llegada) {
        return new Traslado(fechaInicio, pesoBruto, unidadPeso, bultos, partida, llegada, vehiculos, conductores, indicadores);
    }
}
