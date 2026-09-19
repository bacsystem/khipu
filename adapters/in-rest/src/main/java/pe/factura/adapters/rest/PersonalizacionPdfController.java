package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.factura.adapters.rest.dto.PersonalizacionPdfRequest;
import pe.factura.adapters.rest.dto.PersonalizacionPdfResponse;
import pe.factura.application.port.in.PersonalizarPdfUseCase;
import pe.factura.domain.tenant.LogoPdf;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/v1/empresa")
@RequiredArgsConstructor
@Tag(name = "Personalización del PDF", description = """
        Diseño de la representación impresa de la empresa: plantilla, color primario, logo, pie de página y observaciones por
        defecto. No afecta al XML ni a lo que se envía a SUNAT; al cambiarlo, los PDF se regeneran con el nuevo diseño la
        próxima vez que se piden (los ya enviados por correo no cambian).""")
public class PersonalizacionPdfController {
    private final PersonalizarPdfUseCase personalizar;

    @GetMapping("/personalizacion-pdf")
    @Operation(summary = "Ver el diseño del PDF")
    public ApiResponse<PersonalizacionPdfResponse> ver(HttpServletRequest req) {
        return ApiResponse.ok(PersonalizacionPdfResponse.de(personalizar.obtener(TenantActual.id(req))));
    }

    @PutMapping("/personalizacion-pdf")
    @Operation(summary = "Guardar el diseño del PDF", description = "Reemplaza plantilla, color, pie y observaciones por defecto (envíe vacío o `null` para borrar un texto). El logo no cambia: use `PUT/DELETE /v1/empresa/logo`. `422 PERSONALIZACION_INVALIDA` si algún valor no cumple el formato.")
    public ApiResponse<PersonalizacionPdfResponse> guardar(HttpServletRequest req, @Valid @RequestBody PersonalizacionPdfRequest body) {
        return ApiResponse.ok(PersonalizacionPdfResponse.de(personalizar.actualizar(TenantActual.id(req), body.aDominio())));
    }

    @GetMapping("/personalizacion-pdf/vista-previa")
    @Operation(summary = "Vista previa del diseño", description = """
            PDF de una factura de ejemplo con el diseño indicado en los parámetros (los que se omitan toman el valor guardado)
            y el logo actual de la empresa. No guarda nada: sirve para probar antes de `PUT`.""")
    public ResponseEntity<byte[]> vistaPrevia(HttpServletRequest req,
                                              @Parameter(description = "clasico | moderno | sutil | corporativo | gris") @RequestParam(required = false) String plantilla,
                                              @Parameter(description = "Hexadecimal, p. ej. %231F5F4A") @RequestParam(name = "color_primario", required = false) String colorPrimario,
                                              @RequestParam(name = "pie_de_pagina", required = false) String pieDePagina,
                                              @RequestParam(name = "observaciones_por_defecto", required = false) String observacionesPorDefecto) {
        UUID t = TenantActual.id(req);
        var actual = personalizar.obtener(t);
        var diseño = new PersonalizacionPdfRequest(plantilla == null ? actual.plantilla().nombre() : plantilla, colorPrimario == null ? actual.colorPrimario() : colorPrimario,
                pieDePagina == null ? actual.pieDePagina() : pieDePagina, observacionesPorDefecto == null ? actual.observacionesPorDefecto() : observacionesPorDefecto).aDominio();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"vista-previa.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(personalizar.vistaPrevia(t, diseño));
    }

    @PutMapping(value = "/logo", consumes = "multipart/form-data")
    @Operation(summary = "Cargar el logo", description = "PNG o JPEG de hasta 200 KB (`multipart/form-data`, campo `archivo`); se imprime en la cabecera del PDF escalado a 55×18 mm. Reemplaza el anterior. `422 LOGO_INVALIDO` si no es PNG/JPEG o pesa más.")
    public ApiResponse<PersonalizacionPdfResponse> cargarLogo(HttpServletRequest req, @RequestParam("archivo") MultipartFile archivo) throws IOException {
        return ApiResponse.ok(PersonalizacionPdfResponse.de(personalizar.cargarLogo(TenantActual.id(req), archivo.getBytes())));
    }

    @GetMapping("/logo")
    @Operation(summary = "Descargar el logo actual", description = "`404 NO_ENCONTRADO` si la empresa no cargó ninguno.")
    public ResponseEntity<byte[]> logo(HttpServletRequest req) {
        byte[] logo = personalizar.logo(TenantActual.id(req));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(LogoPdf.tipoContenido(LogoPdf.extension(logo)))).header(HttpHeaders.CACHE_CONTROL, "no-store").body(logo);
    }

    @DeleteMapping("/logo")
    @Operation(summary = "Quitar el logo")
    public ApiResponse<PersonalizacionPdfResponse> borrarLogo(HttpServletRequest req) {
        return ApiResponse.ok(PersonalizacionPdfResponse.de(personalizar.borrarLogo(TenantActual.id(req))));
    }
}
