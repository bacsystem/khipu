package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.plataforma.TipoDeAviso;

/** Qué se le avisa a un cliente (#197). El motivo exacto (por vencer o vencido) lo decide el backend según la situación de la empresa. */
public record AvisarRequest(@Schema(example = "CERTIFICADO", allowableValues = {"CERTIFICADO", "CREDENCIALES_SOL"}) TipoDeAviso tipo) {}
