package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

/**
 * Contenido del código QR de la representación impresa (R.S. 300-2014/SUNAT, anexo): los campos separados por {@code |}
 * y en este orden: RUC emisor, tipo de comprobante (catálogo 01), serie, número, IGV total, importe total, fecha de emisión,
 * tipo y número de documento del adquirente (catálogo 06) y el valor resumen, que es el {@code DigestValue} de la firma.
 * La fecha va en ISO ({@code yyyy-MM-dd}), como {@code cbc:IssueDate} del XML; el QR cierra con un {@code |} final.
 */
public final class CodigoQr {
    private CodigoQr() {}

    public static String contenido(Comprobante c, String rucEmisor) {
        if (c.numero() == null || c.hash() == null)
            throw new DomainException("SIN_FIRMA", "El comprobante " + c.serie() + " aún no está numerado y firmado: no tiene código QR");
        return String.join("|", rucEmisor, c.tipo().codigo(), c.serie(), String.valueOf(c.numero()),
                c.totales().igv().toPlainString(), c.totales().total().toPlainString(), c.fechaEmision().toString(),
                c.receptor().tipoDoc(), c.receptor().numDoc(), c.hash()) + "|";
    }
}
