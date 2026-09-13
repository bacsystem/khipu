package pe.factura.adapters.rest.dto;

import pe.factura.domain.documento.Comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ComprobanteResponse(UUID id, String tipo, String serie, Long numero, LocalDate fechaEmision, String moneda,
                                  String estadoDocumento, String hash, Integer intentos, String ultimoError,
                                  CdrDto cdr, TotalesDto totales, Map<String, String> enlaces) {
    public record CdrDto(String codigo, String descripcion, List<String> observaciones) {}
    public record TotalesDto(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto, BigDecimal igv, BigDecimal total) {}

    public static ComprobanteResponse de(Comprobante c, String base) {
        String p = base + "/" + c.id();
        return new ComprobanteResponse(c.id(), c.tipo().codigo(), c.serie(), c.numero(), c.fechaEmision(), c.moneda(),
                c.estado().name(), c.hash(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : new CdrDto(c.cdr().codigo(), c.cdr().descripcion(), c.cdr().observaciones()),
                new TotalesDto(c.totales().gravado(), c.totales().exonerado(), c.totales().inafecto(), c.totales().igv(), c.totales().total()),
                Map.of("xml", p + "/xml", "cdr", p + "/cdr"));
    }
}
