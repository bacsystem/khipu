/**
 * #5: los tipos a mano de `lib/api/*.ts` frente al OpenAPI de la API (`openapi.d.ts`). Solo tipos: `tsc` (y por tanto `npm run typecheck`, el lint y el hook de commit) falla aquí si
 * el backend renombra o quita un campo que el portal usa, o le cambia la forma. Ver `contrato.ts` para qué se compara y por qué los tipos a mano se conservan.
 *
 * Cuando falla: se regenera `openapi.d.ts` (`npm run generate:api` contra un backend nuevo), el error nombra el camino de la clave (`totales.total(ya no existe)`), y se corrige el
 * tipo a mano y lo que lo usa. Un tipo nuevo del portal que modele una respuesta o un cuerpo de la API se agrega aquí con su esquema.
 *
 * Los esquemas se llaman como la clase Java (springdoc usa el nombre simple), por eso dos registros anidados con el mismo nombre se tapan entre sí: lo evita
 * `NombresDeEsquemasOpenApiTest` en el backend, y las respuestas de `ComprobanteResponse` llevan nombres `…Respuesta` frente a los de `FacturaRequest`.
 */
import type { components } from "./openapi";
import type { Contrato, ContratoDePeticion, Verifica } from "./contrato";
import type { Comprobante, ItemComprobante, Receptor, Baja, Anticipo, Detraccion, FormaPago, RetencionIgv, Percepcion, Exportacion, DescuentoAplicado, CargoAplicado, EventoComprobante, NotaResumen, Hidrobiologico, TransporteCarga, ResumenDeFacturas } from "./facturas";
import type { Empresa, EmpresaDetalle, Domicilio, PersonalizacionPdf, CredencialesApiSunat } from "./empresas";
import type { Validez } from "./validez";
import type { Establecimiento } from "./establecimientos";
import type { Serie } from "./series";
import type { ApiKeyResumen } from "./api-keys";
import type { AceptacionDeTerminos, DatosDeRegistro, Usuario, Tokens } from "./auth";
import type { MiCuenta } from "./cuenta";
import type { PlanPublicado } from "./planes-publicados";
import type { Webhook, EntregaWebhook, WebhookCreado } from "./webhooks";
import type { ClienteGuardado, DatosDeCliente } from "./clientes";
import type { ProductoGuardado, DatosDeProducto } from "./productos";
import type { BannerVigente } from "./banner";
import type { AccesoDeSoporte } from "./accesos-de-soporte";
import type { Administrador, AdminDesafio, AdminConfiguracion, AdminSesion, AdminSesionNueva } from "./admin-auth";
import type { AltaAsistidaCreada, AltaAsistida } from "./admin-alta";
import type { TextoDeCorreo, BannerConfigurado, PlantillaDeCorreo, RemitenteConfigurado } from "./admin-configuracion";
import type { CuentaConsumo, ConsumoDeCuentas } from "./admin-consumo";
import type { CuentaAdmin } from "./admin-cuentas";
import type { EmpresaAdmin } from "./admin-empresas";
import type { ErrorDeEmision } from "./admin-errores";
import type { Franja, ResumenesSinRespuesta, BoletasSinInformar, MonitorDeEmision } from "./admin-monitor";
import type { ImpersonacionAdmin } from "./admin-impersonacion";
import type { PagoAdmin, CuerpoDePago } from "./admin-pagos";
import type { PlanAdmin, LimitesAdmin, CuerpoDePlan } from "./admin-planes";
import type { PlanResumenAdmin, PlanDeCuentaAdmin, CuerpoDeCambioDePlan } from "./admin-plan-de-cuenta";
import type { CambioDeEntornoAdmin, ResultadoDeConexionAdmin, ApiKeyRevocadaAdmin } from "./admin-acciones-empresa";
import type { EmpresaDetalleAdmin, DomicilioEmpresa, SerieEmpresa, EstablecimientoEmpresa, ApiKeyEmpresa, ComprobanteEmpresa, EventoEmpresa, TareaEmpresa, OutboxEmpresa, PdfEmpresa, CdrEmpresa } from "./admin-empresa-detalle";
import type { CuentaDetalleAdmin, UsuarioCuenta, EmpresaCuenta, ComprobanteReciente, EventoReciente } from "./admin-cuenta-detalle";
import type { InformeDeIntegridad, ProblemaDeIntegridad } from "./admin-integridad";
import type { ComprobanteAdmin } from "./admin-comprobante";
import type { CertificadoEnRiesgo, AvisoEnviado, SolFallando, CuentaDelAviso, UltimoAviso } from "./admin-avisos";
import type { DestinatarioAdmin } from "./admin-acceso";

type S = components["schemas"];

export type ContratoDeLaApi = [
  // facturas
  Verifica<Contrato<Comprobante, S["ComprobanteResponse"]>>,
  Verifica<Contrato<ItemComprobante, S["ItemRespuesta"]>>,
  Verifica<Contrato<Receptor, S["ReceptorDto"]>>,
  Verifica<Contrato<Baja, S["BajaResponse"]>>,
  Verifica<Contrato<Anticipo, S["AnticipoRespuesta"]>>,
  Verifica<Contrato<Detraccion, S["DetraccionRespuesta"]>>,
  Verifica<Contrato<FormaPago, S["FormaPagoRespuesta"]>>,
  Verifica<Contrato<RetencionIgv, S["RetencionRespuesta"]>>,
  Verifica<Contrato<Percepcion, S["PercepcionRespuesta"]>>,
  Verifica<Contrato<Exportacion, S["ExportacionRespuesta"]>>,
  Verifica<Contrato<DescuentoAplicado, S["DescuentoRespuesta"]>>,
  Verifica<Contrato<CargoAplicado, S["CargoRespuesta"]>>,
  Verifica<Contrato<EventoComprobante, S["EventoDto"]>>,
  Verifica<Contrato<NotaResumen, S["NotaResumenDto"]>>,
  Verifica<Contrato<Hidrobiologico, S["HidrobiologicoDto"]>>,
  Verifica<Contrato<TransporteCarga, S["TransporteCarga"]>>,
  Verifica<Contrato<ResumenDeFacturas, S["ResumenDeComprobantesResponse"]>>,
  // empresas
  Verifica<Contrato<Empresa, S["EmpresaVistaResponse"]>>,
  Verifica<Contrato<EmpresaDetalle, S["EmpresaResponse"]>>,
  Verifica<Contrato<Domicilio, S["DomicilioResponse"]>>,
  Verifica<Contrato<PersonalizacionPdf, S["PersonalizacionPdfResponse"]>>,
  Verifica<ContratoDePeticion<CredencialesApiSunat, S["CredencialesApiSunatRequest"]>>,
  // consultas
  Verifica<Contrato<Validez, S["ValidezResponse"]>>,
  // establecimientos
  Verifica<Contrato<Establecimiento, S["EstablecimientoResponse"]>>,
  // series
  Verifica<Contrato<Serie, S["SerieResponse"]>>,
  // api-keys
  Verifica<Contrato<ApiKeyResumen, S["ApiKeyResumenResponse"]>>,
  // auth
  Verifica<Contrato<Usuario, S["UsuarioResponse"]>>,
  Verifica<Contrato<Tokens, S["TokensResponse"]>>,
  Verifica<ContratoDePeticion<DatosDeRegistro, S["RegistroRequest"]>>,
  Verifica<Contrato<AceptacionDeTerminos, S["AceptacionDeTerminosResponse"]>>,
  // cuenta
  Verifica<Contrato<MiCuenta, S["MiCuentaResponse"]>>,
  // planes-publicados
  Verifica<Contrato<PlanPublicado, S["PlanPublicado"]>>,
  // webhooks
  Verifica<Contrato<Webhook, S["Webhook"]>>,
  Verifica<Contrato<EntregaWebhook, S["Entrega"]>>,
  // clientes
  Verifica<Contrato<ClienteGuardado, S["Cliente"]>>,
  Verifica<ContratoDePeticion<DatosDeCliente, S["ClienteCrear"]>>,
  // productos
  Verifica<Contrato<ProductoGuardado, S["Producto"]>>,
  Verifica<ContratoDePeticion<DatosDeProducto, S["ProductoGuardar"]>>,
  // banner
  Verifica<Contrato<BannerVigente, S["BannerPublico"]>>,
  // accesos-de-soporte
  Verifica<Contrato<AccesoDeSoporte, S["AccesoDeSoporteResponse"]>>,
  // admin-auth
  Verifica<Contrato<Administrador, S["AdministradorResponse"]>>,
  Verifica<Contrato<AdminDesafio, S["AdminDesafioResponse"]>>,
  Verifica<Contrato<AdminConfiguracion, S["AdminConfiguracionResponse"]>>,
  Verifica<Contrato<AdminSesion, S["AdminSesionResponse"]>>,
  // admin-alta
  Verifica<Contrato<AltaAsistidaCreada, S["AltaAsistidaResponse"]>>,
  // admin-configuracion
  Verifica<Contrato<TextoDeCorreo, S["TextoDeCorreo"]>>,
  Verifica<Contrato<BannerConfigurado, S["Banner"]>>,
  // admin-consumo
  Verifica<Contrato<CuentaConsumo, S["CuentaConsumo"]>>,
  Verifica<Contrato<ConsumoDeCuentas, S["ConsumoDeCuentasResponse"]>>,
  // admin-cuentas
  Verifica<Contrato<CuentaAdmin, S["CuentaAdminResponse"]>>,
  // admin-empresas
  Verifica<Contrato<EmpresaAdmin, S["EmpresaAdminResponse"]>>,
  // admin-errores
  Verifica<Contrato<ErrorDeEmision, S["ErrorDeEmisionResponse"]>>,
  // admin-monitor
  Verifica<Contrato<Franja, S["FranjaResponse"]>>,
  Verifica<Contrato<ResumenesSinRespuesta, S["ResumenesSinRespuestaResponse"]>>,
  Verifica<Contrato<BoletasSinInformar, S["BoletasSinInformarResponse"]>>,
  Verifica<Contrato<MonitorDeEmision, S["MonitorDeEmisionResponse"]>>,
  // admin-impersonacion
  Verifica<Contrato<ImpersonacionAdmin, S["ImpersonacionResponse"]>>,
  // admin-pagos
  Verifica<Contrato<PagoAdmin, S["PagoResponse"]>>,
  // admin-planes
  Verifica<Contrato<PlanAdmin, S["PlanResponse"]>>,
  // admin-plan-de-cuenta
  Verifica<Contrato<PlanResumenAdmin, S["PlanResumenResponse"]>>,
  // admin-acciones-empresa
  Verifica<Contrato<CambioDeEntornoAdmin, S["CambioDeEntornoResponse"]>>,
  Verifica<Contrato<ResultadoDeConexionAdmin, S["ResultadoDeConexionResponse"]>>,
  // admin-empresa-detalle
  Verifica<Contrato<EmpresaDetalleAdmin, S["EmpresaDetalleResponse"]>>,
  Verifica<Contrato<DomicilioEmpresa, S["DomicilioDeDetalle"]>>,
  Verifica<Contrato<SerieEmpresa, S["SerieDeDetalle"]>>,
  Verifica<Contrato<EstablecimientoEmpresa, S["EstablecimientoDeDetalle"]>>,
  Verifica<Contrato<ApiKeyEmpresa, S["ApiKeyDeDetalle"]>>,
  Verifica<Contrato<ComprobanteEmpresa, S["ComprobanteDeEmpresa"]>>,
  Verifica<Contrato<EventoEmpresa, S["EventoDeEmpresa"]>>,
  Verifica<Contrato<TareaEmpresa, S["TareaResponse"]>>,
  Verifica<Contrato<OutboxEmpresa, S["OutboxDeEmpresa"]>>,
  Verifica<Contrato<PdfEmpresa, S["PdfResponse"]>>,
  Verifica<Contrato<CdrEmpresa, S["CdrResponse"]>>,
  // admin-cuenta-detalle
  Verifica<Contrato<CuentaDetalleAdmin, S["CuentaDetalleResponse"]>>,
  Verifica<Contrato<UsuarioCuenta, S["UsuarioDeCuenta"]>>,
  Verifica<Contrato<EmpresaCuenta, S["EmpresaDeCuenta"]>>,
  Verifica<Contrato<ComprobanteReciente, S["ComprobanteDeCuenta"]>>,
  Verifica<Contrato<EventoReciente, S["EventoDeCuenta"]>>,
  // #5 revisión: el resto de lo que el portal modela de la API
  Verifica<Contrato<PlantillaDeCorreo, S["Plantilla"]>>,
  Verifica<Contrato<RemitenteConfigurado, S["Remitente"]>>,
  Verifica<Contrato<WebhookCreado, S["Creado"]>>,
  Verifica<Contrato<InformeDeIntegridad, S["Informe"]>>,
  Verifica<Contrato<ProblemaDeIntegridad, S["Problema"]>>,
  Verifica<Contrato<ComprobanteAdmin, S["FichaDeComprobanteResponse"]>>,
  Verifica<Contrato<LimitesAdmin, S["LimitesDto"]>>,
  Verifica<ContratoDePeticion<CuerpoDePlan, S["PlanRequest"]>>,
  Verifica<Contrato<PlanDeCuentaAdmin, S["PlanDeCuentaResponse"]>>,
  Verifica<ContratoDePeticion<CuerpoDeCambioDePlan, S["CambiarPlanRequest"]>>,
  Verifica<Contrato<AdminSesionNueva, S["AdminSesionNuevaResponse"]>>,
  Verifica<Contrato<ApiKeyRevocadaAdmin, S["ApiKeyRevocadaResponse"]>>,
  Verifica<Contrato<CertificadoEnRiesgo, S["Certificado"]>>,
  Verifica<Contrato<AvisoEnviado, S["Enviado"]>>,
  Verifica<Contrato<SolFallando, S["Sol"]>>,
  Verifica<Contrato<CuentaDelAviso, S["Cuenta"]>>,
  Verifica<Contrato<UltimoAviso, S["Ultimo"]>>,
  Verifica<Contrato<DestinatarioAdmin, S["DestinatarioResponse"]>>,
  Verifica<ContratoDePeticion<AltaAsistida, S["AltaAsistidaRequest"]>>,
  Verifica<ContratoDePeticion<CuerpoDePago, S["RegistrarPagoRequest"]>>,
];
