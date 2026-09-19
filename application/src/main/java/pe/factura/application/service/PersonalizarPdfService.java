package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.PersonalizarPdfUseCase;
import pe.factura.application.port.out.DocumentStorage;
import pe.factura.application.port.out.PdfGenerator;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.CodigoQr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.Referencias;
import pe.factura.domain.documento.TipoAfectacionIgv;
import pe.factura.domain.tenant.LogoPdf;
import pe.factura.domain.tenant.PersonalizacionPdf;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class PersonalizarPdfService implements PersonalizarPdfUseCase {
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final PdfGenerator pdf;
    private final Clock clock;

    @Override public PersonalizacionPdf obtener(UUID tenantId) { return tenant(tenantId).personalizacionPdf(); }

    @Override public PersonalizacionPdf actualizar(UUID tenantId, PersonalizacionPdf diseño) {
        Tenant t = tenant(tenantId);
        Tenant nuevo = t.conPersonalizacionPdf(t.personalizacionPdf().conDiseñoDe(diseño));
        tenants.guardar(nuevo);
        return nuevo.personalizacionPdf();
    }

    @Override public PersonalizacionPdf cargarLogo(UUID tenantId, byte[] logo) {
        Tenant t = tenant(tenantId);
        String key = LogoPdf.clave(tenantId, logo);   // valida cabecera y tamaño
        // La cabecera no garantiza que la imagen decodifique: un PNG truncado pasaría y rompería todos los PDF de la empresa hasta
        // que alguien lo borre. Se renderiza una vista previa con el candidato antes de guardarlo.
        try {
            Comprobante ejemplo = ejemplo(t, LocalDate.now(clock));
            pdf.generar(ejemplo, t, CodigoQr.contenido(ejemplo, t.ruc()), logo);
        } catch (RuntimeException e) {
            throw new DomainException("LOGO_INVALIDO", "El logo no se pudo decodificar como imagen: " + e.getMessage(), e);
        }
        String anterior = t.personalizacionPdf().logoKey();
        storage.guardar(key, logo);
        Tenant nuevo = t.conPersonalizacionPdf(t.personalizacionPdf().conLogo(key));
        tenants.guardar(nuevo);
        if (anterior != null && !anterior.equals(key)) storage.borrar(anterior);
        return nuevo.personalizacionPdf();
    }

    @Override public PersonalizacionPdf borrarLogo(UUID tenantId) {
        Tenant t = tenant(tenantId);
        String anterior = t.personalizacionPdf().logoKey();
        Tenant nuevo = t.conPersonalizacionPdf(t.personalizacionPdf().sinLogo());
        tenants.guardar(nuevo);
        if (anterior != null) storage.borrar(anterior);
        return nuevo.personalizacionPdf();
    }

    @Override public byte[] logo(UUID tenantId) {
        PersonalizacionPdf p = obtener(tenantId);
        if (!p.tieneLogo() || !storage.existe(p.logoKey())) throw new DomainException("NO_ENCONTRADO", "La empresa no tiene logo");
        return storage.leer(p.logoKey());
    }

    @Override public byte[] vistaPrevia(UUID tenantId, PersonalizacionPdf diseño) {
        Tenant t = tenant(tenantId);
        Tenant conDiseño = t.conPersonalizacionPdf(t.personalizacionPdf().conDiseñoDe(diseño));
        Comprobante ejemplo = ejemplo(t, LocalDate.now(clock));
        return pdf.generar(ejemplo, conDiseño, CodigoQr.contenido(ejemplo, t.ruc()), logoDe(storage, conDiseño.personalizacionPdf()));
    }

    /** Bytes del logo de la empresa, o {@code null} si no tiene o el archivo ya no está en storage. */
    static byte[] logoDe(DocumentStorage storage, PersonalizacionPdf p) {
        return p.tieneLogo() && storage.existe(p.logoKey()) ? storage.leer(p.logoKey()) : null;
    }

    /** Factura ficticia con lo que más se ve en el diseño: varias líneas, descuento, crédito en cuotas, detracción y observaciones. */
    private Comprobante ejemplo(Tenant t, LocalDate hoy) {
        List<Item> items = List.of(
                new Item("SRV-001", "Servicio de consultoría (mes)", "ZZ", BigDecimal.ONE, new BigDecimal("2360.00"), TipoAfectacionIgv.GRAVADO),
                new Item("LIC-PRO", "Licencia de software — plan Pro", "NIU", new BigDecimal("2"), new BigDecimal("354.00"), TipoAfectacionIgv.GRAVADO),
                new Item("LIB-01", "Manual impreso", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO));
        BigDecimal total = new BigDecimal("3118.00");
        FormaPago credito = FormaPago.credito(total, List.of(new FormaPago.Cuota(new BigDecimal("1559.00"), hoy.plusDays(30)), new FormaPago.Cuota(new BigDecimal("1559.00"), hoy.plusDays(60))));
        Detraccion detraccion = new Detraccion("022", new BigDecimal("12"), null, t.cuentaDetracciones() == null ? "00-000-123456" : t.cuentaDetracciones(), "001");
        Comprobante c = Comprobante.factura(t.id(), "F001", hoy, "PEN", "1001", new Receptor("6", "20100070970", "EMPRESA DEMO S.A.C.", "Av. Javier Prado Este 123, San Isidro, Lima"), items)
                .fechaVencimiento(hoy.plusDays(60))
                .formaPago(credito)
                .detraccion(detraccion)
                .referencias(new Referencias("OC-2026-0457", List.of(), List.of()))
                .crear(clock);
        c.asignarNumero(123, t.ruc());
        c.firmar("EjEmPlO0000000000000000000000000000=", "vista-previa");
        c.anotar("Servicio prestado según orden de compra del cliente.");
        return c;
    }

    private Tenant tenant(UUID tenantId) {
        return tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
    }
}
