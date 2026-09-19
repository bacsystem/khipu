package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.factura.adapters.rest.dto.ApiKeyResponse;
import pe.factura.adapters.rest.dto.ApiKeyResumenResponse;
import pe.factura.adapters.rest.dto.CredencialesSolRequest;
import pe.factura.adapters.rest.dto.DatosFiscalesRequest;
import pe.factura.adapters.rest.dto.EmpresaResponse;
import pe.factura.adapters.rest.dto.EstablecimientoRequest;
import pe.factura.adapters.rest.dto.EstablecimientoResponse;
import pe.factura.adapters.rest.dto.SerieRequest;
import pe.factura.adapters.rest.dto.SerieResponse;
import pe.factura.application.port.in.AdministrarTenantUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Tenant;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@Tag(name = "Empresa", description = """
        Configuración de la empresa emisora autenticada: datos fiscales, certificado digital, credenciales SOL, series y
        API keys. `X-Api-Key` identifica a la empresa; desde el portal se usa la sesión más la cabecera `X-Empresa`.
        Los secretos (clave SOL, contraseña del certificado) se cifran en reposo y nunca vuelven por la API.""")
@RequiredArgsConstructor
public class EmpresaController {
    private final AdministrarTenantUseCase admin;

    @GetMapping("/empresa")
    @Operation(summary = "Ver la empresa", description = "RUC, razón social, entorno SUNAT (`BETA` u homologación / `PRODUCCION`), si tiene credenciales SOL, la vigencia del certificado, el domicilio fiscal y la cuenta de detracciones. Nunca devuelve secretos.")
    public ApiResponse<EmpresaResponse> ver(HttpServletRequest req) {
        return ApiResponse.ok(EmpresaResponse.de(admin.obtener(TenantActual.id(req))));
    }

    @PostMapping(value = "/empresa/certificado", consumes = "multipart/form-data")
    @Operation(summary = "Cargar el certificado digital", description = """
            Sube el certificado PKCS#12 (`.p12`/`.pfx`) con el que se firmarán los XML y su contraseña (`multipart/form-data`,
            campos `archivo` y `clave`). El RUC de la empresa debe figurar en el campo `OU` del certificado; si no,
            `422 CERTIFICADO_INVALIDO`. Reemplaza el certificado anterior.""")
    public ResponseEntity<Void> certificado(HttpServletRequest req, @RequestParam("archivo") MultipartFile archivo, @RequestParam("clave") String clave) throws IOException {
        admin.cargarCertificado(TenantActual.id(req), archivo.getBytes(), clave);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/empresa/datos-fiscales")
    @Operation(summary = "Guardar domicilio fiscal, cuenta de detracciones y nombre comercial", description = """
            Domicilio fiscal del emisor (ubigeo del catálogo 13, dirección, urbanización, establecimiento anexo) que khipu escribe en
            `cac:RegistrationAddress` de cada XML, la cuenta de detracciones del Banco de la Nación que se usa cuando una factura
            sujeta a detracción no indica la suya, y el nombre comercial (`cac:PartyName`). Reemplaza los tres valores: envíe `null`
            en el que quiera borrar. Errores: `422 DOMICILIO_INVALIDO` (mensaje con la regla SUNAT: 4093 ubigeo, 4094 dirección,
            3030 establecimiento), `422 CUENTA_DETRACCIONES_INVALIDA` o `422 NOMBRE_COMERCIAL_INVALIDO` (4092). `padron_tasa_especial_igv`
            activa la tasa reducida del IGV (padrón de restaurantes y hoteles) para los comprobantes que se emitan desde entonces.""")
    public ApiResponse<EmpresaResponse> datosFiscales(HttpServletRequest req, @Valid @RequestBody DatosFiscalesRequest body) {
        return ApiResponse.ok(EmpresaResponse.de(admin.actualizarDatosFiscales(TenantActual.id(req),
                body.domicilio() == null ? null : body.domicilio().aDominio(), body.cuentaDetracciones(), body.nombreComercial(), body.tasaEspecial())));
    }

    @PutMapping("/empresa/credenciales-sol")
    @Operation(summary = "Guardar las credenciales SOL", description = "Usuario secundario SOL (sin el RUC; khipu antepone `RUC+usuario` al enviar) y su clave. Obligatorias antes de enviar comprobantes a SUNAT.")
    public ResponseEntity<Void> credencialesSol(HttpServletRequest req, @Valid @RequestBody CredencialesSolRequest body) {
        admin.cargarCredencialesSol(TenantActual.id(req), body.usuario(), body.clave());
        return ResponseEntity.noContent().build();
    }

    // La gestión de API keys exige sesión del portal: una key filtrada no debe poder crear otras ni revocar las del tenant.
    @PostMapping("/empresa/api-keys")
    @Operation(summary = "Crear una API key", description = "Genera una llave `fk_…` para integraciones. **El secreto se muestra una sola vez** en esta respuesta; después solo se ve el prefijo. Requiere sesión del portal (no se puede crear con otra API key).")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> apiKey(HttpServletRequest req) {
        CuentaActual.exigirSesion(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(new ApiKeyResponse(admin.crearApiKey(TenantActual.id(req)))));
    }

    @GetMapping("/empresa/api-keys")
    @Operation(summary = "Listar API keys", description = "Prefijo, estado y fechas de cada llave; nunca el secreto. Requiere sesión del portal.")
    public ApiResponse<List<ApiKeyResumenResponse>> apiKeys(HttpServletRequest req) {
        CuentaActual.exigirSesion(req);
        return ApiResponse.ok(admin.listarApiKeys(TenantActual.id(req)).stream().map(ApiKeyResumenResponse::de).toList());
    }

    @DeleteMapping("/empresa/api-keys/{id}")
    @Operation(summary = "Revocar una API key", description = "La llave deja de autenticar al instante y sin período de gracia; es idempotente. `404 NO_ENCONTRADO` si no pertenece a la empresa. Requiere sesión del portal.")
    public ResponseEntity<Void> revocarApiKey(HttpServletRequest req, @PathVariable UUID id) {
        CuentaActual.exigirSesion(req);
        admin.revocarApiKey(TenantActual.id(req), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/series")
    @Operation(summary = "Crear una serie", description = """
            Registra una serie de numeración. El prefijo indica el tipo: `F###` factura, `B###` boleta, `FC##`/`BC##` nota de
            crédito, `FD##`/`BD##` nota de débito (3 alfanuméricos tras el prefijo). `correlativo_inicial` es el último
            número ya usado en otro sistema (0 si es nueva): khipu emitirá desde el siguiente. `409 DUPLICADO` si ya existe.""")
    public ResponseEntity<Void> crearSerie(HttpServletRequest req, @Valid @RequestBody SerieRequest body) {
        admin.crearSerie(TenantActual.id(req), TipoDocumento.porCodigo(body.tipo()), body.serie(), body.correlativoInicial() == null ? 0 : body.correlativoInicial(), body.establecimiento());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/series")
    @Operation(summary = "Listar series", description = "Series de la empresa con su tipo, último número asignado y si acepta emisiones.")
    public ApiResponse<List<SerieResponse>> series(HttpServletRequest req) {
        return ApiResponse.ok(admin.listarSeries(TenantActual.id(req)).stream()
                .map(s -> new SerieResponse(s.tipo().codigo(), s.codigo(), s.ultimoNumero(), s.activa(), s.establecimiento()))
                .toList());
    }

    @GetMapping("/empresa/establecimientos")
    @Operation(summary = "Listar establecimientos", description = """
            Puntos desde los que emite la empresa: el domicilio fiscal como `0000` (`principal: true`, si está configurado en
            datos fiscales) más los establecimientos anexos registrados, activos y dados de baja. Cada serie se asigna a uno
            (`POST /v1/series`) y sus comprobantes salen con ese `cac:RegistrationAddress` y `AddressTypeCode` (regla 3030).""")
    public ApiResponse<List<EstablecimientoResponse>> establecimientos(HttpServletRequest req) {
        UUID tenantId = TenantActual.id(req);
        Tenant t = admin.obtener(tenantId);
        List<EstablecimientoResponse> lista = new java.util.ArrayList<>();
        if (t.domicilio() != null) lista.add(EstablecimientoResponse.principal(t.domicilio()));
        admin.listarEstablecimientos(tenantId).stream().map(EstablecimientoResponse::de).forEach(lista::add);
        return ApiResponse.ok(lista);
    }

    @PostMapping("/empresa/establecimientos")
    @Operation(summary = "Registrar un establecimiento anexo", description = """
            Alta de un anexo con el código de su ficha RUC (4 dígitos, distinto de `0000`), un nombre y su domicilio (mismas
            reglas que el fiscal: 4093 ubigeo, 4094 dirección). Si el código ya existe se actualiza (mismo efecto que `PUT`).
            `422 ESTABLECIMIENTO_INVALIDO` o `422 DOMICILIO_INVALIDO`.""")
    public ResponseEntity<ApiResponse<EstablecimientoResponse>> crearEstablecimiento(HttpServletRequest req, @Valid @RequestBody EstablecimientoRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(EstablecimientoResponse.de(
                admin.guardarEstablecimiento(TenantActual.id(req), body.codigo(), body.nombre(), body.domicilio().aDominio()))));
    }

    @PutMapping("/empresa/establecimientos/{codigo}")
    @Operation(summary = "Editar un establecimiento anexo", description = "Reemplaza nombre y domicilio; conserva el estado. El `0000` se edita en `PUT /v1/empresa/datos-fiscales`.")
    public ApiResponse<EstablecimientoResponse> editarEstablecimiento(HttpServletRequest req, @PathVariable String codigo, @Valid @RequestBody EstablecimientoRequest body) {
        if (!codigo.equals(body.codigo())) throw new DomainException("ESTABLECIMIENTO_INVALIDO", "El código de la ruta (" + codigo + ") y del cuerpo (" + body.codigo() + ") no coinciden");
        return ApiResponse.ok(EstablecimientoResponse.de(admin.guardarEstablecimiento(TenantActual.id(req), codigo, body.nombre(), body.domicilio().aDominio())));
    }

    @DeleteMapping("/empresa/establecimientos/{codigo}")
    @Operation(summary = "Dar de baja un establecimiento anexo", description = """
            Baja lógica: deja de admitir series nuevas y emisiones; los comprobantes ya emitidos conservan su domicilio.
            `409 ESTABLECIMIENTO_EN_USO` si tiene series activas; `404 NO_ENCONTRADO`; el `0000` no se puede dar de baja.""")
    public ResponseEntity<Void> darDeBajaEstablecimiento(HttpServletRequest req, @PathVariable String codigo) {
        if (Domicilio.ESTABLECIMIENTO_PRINCIPAL.equals(codigo)) throw new DomainException("ESTABLECIMIENTO_INVALIDO", "El 0000 es el domicilio fiscal: no se da de baja, se edita en datos fiscales");
        admin.desactivarEstablecimiento(TenantActual.id(req), codigo);
        return ResponseEntity.noContent().build();
    }
}
