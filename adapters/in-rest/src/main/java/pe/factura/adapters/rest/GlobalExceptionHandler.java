package pe.factura.adapters.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.factura.domain.DomainException;

import java.util.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> dominio(DomainException e) {
        HttpStatus st = switch (e.codigo()) {
            case "NO_ENCONTRADO", "SIN_CDR" -> HttpStatus.NOT_FOUND;
            case "DUPLICADO", "ESTADO_NO_ENVIABLE", "NO_ACEPTADO", "ESTABLECIMIENTO_EN_USO", "FUERA_DE_PLAZO", "CDR_YA_DISPONIBLE" -> HttpStatus.CONFLICT;
            case "NO_AUTORIZADO", "CREDENCIALES_INVALIDAS", "SESION_INVALIDA", "CODIGO_INVALIDO" -> HttpStatus.UNAUTHORIZED;
            case "SEGUNDO_FACTOR_NO_CONFIGURADO", "SEGUNDO_FACTOR_YA_CONFIGURADO" -> HttpStatus.CONFLICT;
            // El alta ya se hizo con esa clave pero su respuesta (con la API key) ya no se guarda: no es un dato inválido del pedido.
            case "IDEMPOTENCIA_VENCIDA" -> HttpStatus.CONFLICT;
            case "CORREO_YA_VERIFICADO" -> HttpStatus.CONFLICT;
            // Mandarle un correo de acceso a un usuario desactivado (#183): el pedido es válido pero el estado del usuario no lo permite.
            case "USUARIO_INACTIVO" -> HttpStatus.CONFLICT;
            // Suspender o reactivar una cuenta que ya está en ese estado (#182): el pedido es válido pero el estado ya no lo permite.
            case "CUENTA_YA_SUSPENDIDA", "CUENTA_NO_SUSPENDIDA", "CUENTA_YA_DE_BAJA", "CUENTA_NO_DE_BAJA" -> HttpStatus.CONFLICT;
            // Acciones sobre una empresa (#187): el pedido es válido pero el estado de la empresa no lo permite (ya estaba así, tiene envíos pendientes o le faltan las credenciales SOL).
            case "ENTORNO_SIN_CAMBIOS", "EMPRESA_CON_ENVIOS_PENDIENTES", "API_KEY_YA_REVOCADA", "SOL_NO_CARGADAS" -> HttpStatus.CONFLICT;
            // Gestión de planes (#190): el pedido es válido pero el estado del plan no lo permite (nombre repetido, ya estaba así, es el de las cuentas nuevas o lo usan cuentas).
            case "NOMBRE_DUPLICADO", "PLAN_POR_DEFECTO", "PLAN_EN_USO", "PLAN_YA_ACTIVO", "PLAN_YA_INACTIVO" -> HttpStatus.CONFLICT;
            // Cambio de plan de una cuenta (#191): el plan está fuera de la oferta, o otro administrador cambió el plan de la cuenta en el medio.
            case "PLAN_INACTIVO", "CAMBIO_CONCURRENTE" -> HttpStatus.CONFLICT;
            // Pagos manuales (#194): el pedido es válido pero el estado de la cuenta no lo permite (el mismo apunte repetido, un plan que no vence o un pago que no adelanta nada).
            case "PAGO_DUPLICADO", "PLAN_SIN_VENCIMIENTO", "EXTENSION_SIN_EFECTO" -> HttpStatus.CONFLICT;
            // Cola de errores (#196): el pedido es válido pero el estado del comprobante no lo permite, o cambió en el medio (la guía de errores ya documentaba ESTADO_CONFLICTO como 409).
            // SUNAT_YA_LO_TIENE: antes de descartar se le preguntó a SUNAT y tenía el comprobante (se aplicó su CDR).
            case "ESTADO_NO_DESCARTABLE", "ESTADO_CONFLICTO", "SUNAT_YA_LO_TIENE" -> HttpStatus.CONFLICT;
            // No se pudo preguntarle a SUNAT si ya tiene el comprobante: no es culpa del cliente, y se puede reintentar más tarde.
            case "SUNAT_NO_DISPONIBLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            // Avisos a los clientes (#197): el pedido es válido pero la situación no lo permite (no hay nada que avisar, no hay a quién escribirle, o se avisó lo mismo hace poco).
            case "AVISO_SIN_MOTIVO", "EMPRESA_SIN_CUENTA", "AVISO_RECIENTE" -> HttpStatus.CONFLICT;
            // LIMITE_PLAN (#18): la cuenta llegó al tope de documentos del mes de su plan; se puede volver a emitir el mes siguiente o con otro plan.
            case "DEMASIADOS_INTENTOS", "DEMASIADOS_ENLACES", "DEMASIADOS_INTENTOS_LOGIN", "LIMITE_PLAN" -> HttpStatus.TOO_MANY_REQUESTS;
            // La cuenta está suspendida (#182): el cliente se identificó bien, pero no tiene permiso hasta que la reactiven.
            case "EMPRESA_AJENA", "REQUIERE_SESION", "REGISTRO_CERRADO", "CUENTA_SUSPENDIDA", "REQUIERE_ADMINISTRADOR" -> HttpStatus.FORBIDDEN;
            case "PARAMETRO_INVALIDO", "RANGO_INVALIDO" -> HttpStatus.BAD_REQUEST;
            // El correo saliente es un servicio externo: su fallo no es culpa del cliente ni un bug del servidor.
            case "CORREO_NO_ENVIADO" -> HttpStatus.BAD_GATEWAY;
            // El servidor no tiene SMTP: no es culpa del cliente y reintentar no sirve hasta que el operador lo configure.
            case "CORREO_NO_CONFIGURADO" -> HttpStatus.SERVICE_UNAVAILABLE;
            // Un CDR guardado que no se puede leer es un fallo del servidor, no de la petición.
            case "CDR_CORRUPTO" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(st).body(ApiResponse.error(e.codigo(), e.getMessage()));
    }

    /**
     * Última barrera de las UNIQUE: la carrera entre dos emisiones con el mismo (serie, número) y el alta de una
     * serie repetida caen las dos acá. El mensaje se elige por la tabla del constraint porque decirle «ya existe un
     * documento con esa serie y número» a quien está creando una serie no explica nada. El texto del constraint no
     * se devuelve, solo se usa para decidir.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> duplicado(DuplicateKeyException e) {
        String causa = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        String mensaje = causa.contains("serie") && !causa.contains("documento")
                ? "Ya existe una serie con ese tipo y código"
                : causa.contains("documento")
                ? "Ya existe un documento con esa serie y número"
                : "Ya existe un registro con esos datos";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("DUPLICADO", mensaje));
    }

    /**
     * El único multipart del sistema es el .p12 del certificado. Sin este handler un archivo más grande que el tope
     * cae en el catch-all y devuelve 500 INTERNO, que el portal traduce a «error interno, intenta en unos minutos»:
     * el usuario no tiene forma de saber que el problema es el tamaño del archivo que eligió.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> archivoDemasiadoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.unprocessableEntity()
                .body(ApiResponse.error("ARCHIVO_DEMASIADO_GRANDE", "El archivo supera el tamaño máximo permitido (1 MB). Un certificado .p12 pesa unos pocos KB: conviene verificar que sea el archivo correcto."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validacion(MethodArgumentNotValidException e) {
        Map<String, List<String>> errores = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errores.computeIfAbsent(f.getField(), k -> new ArrayList<>()).add(f.getDefaultMessage()));
        return ResponseEntity.unprocessableEntity().body(ApiResponse.validacion(errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("JSON_INVALIDO", "El cuerpo de la petición no es JSON válido"));
    }

    /** Path/query param con tipo inválido (p. ej. un id que no es UUID): es error del cliente, no del servidor. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("PARAMETRO_INVALIDO", "El parámetro '" + e.getName() + "' no tiene un formato válido"));
    }

    /** Query param obligatorio ausente (p. ej. `desde`/`hasta` de la verificación de integridad): error del cliente. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> parametroAusente(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("PARAMETRO_INVALIDO", "Falta el parámetro '" + e.getParameterName() + "'"));
    }

    /** Ruta inexistente: sin esto caería en el catch-all y respondería 500 "Error interno" (p. ej. un portal más nuevo que el backend). */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> rutaInexistente(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("RUTA_INEXISTENTE", "La ruta no existe en esta versión de la API"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> interno(Exception e) {
        String traceId = UUID.randomUUID().toString();
        log.error("Error interno trace_id={}", traceId, e);
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNO", "Error interno. trace_id=" + traceId));
    }
}
