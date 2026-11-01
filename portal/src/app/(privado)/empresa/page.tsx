import { FormulariosDeEscritura } from "@/lib/solo-lectura";
import { ArrowSquareOutIcon, BuildingsIcon, CertificateIcon, CheckIcon, CloudIcon, EnvelopeSimpleIcon, FileLockIcon, KeyIcon, PaletteIcon, QuestionIcon, SealCheckIcon, ShieldWarningIcon } from "@phosphor-icons/react/dist/ssr";
import { PESO_ICONO } from "khipu-design-system/lib/iconos";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getLocale, getMessages } from "next-intl/server";
import { CertificadoForm } from "@/components/empresa/certificado-form";
import { CredencialesApiSunatForm } from "@/components/empresa/credenciales-api-sunat-form";
import { CredencialesSolForm } from "@/components/empresa/credenciales-sol-form";
import { DatosFiscalesForm } from "@/components/empresa/datos-fiscales-form";
import { GuiaParaEmitir } from "@/components/empresa/guia-para-emitir";
import { NuevaEmpresaDialog } from "@/components/empresa/nueva-empresa-dialog";
import { PersonalizacionPdfForm } from "@/components/empresa/personalizacion-pdf-form";
import { SeccionesEmpresa } from "@/components/empresa/secciones-empresa";
import { listarEmpresas, obtenerEmpresaActual, obtenerPersonalizacionPdf, obtenerPreferenciaDeEnvioAlCliente } from "@/lib/api/empresas";
import { EnvioAlClienteForm } from "@/components/empresa/envio-al-cliente-form";
import { listarSeries } from "@/lib/api/series";
import { pasosParaEmitir } from "@/lib/empresa/guia-emision";
import { seccionesPendientes, seccionInicial } from "@/lib/empresa/secciones";
import { ACCION_SECUNDARIA, ETIQUETA_DATO, TARJETA, TITULO_SECCION } from "@/lib/estilos";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { diasEntre, formatearFecha, formatearFechaHora, hoyLima } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import { Metrica } from "@/components/compartidos/metrica";
import { interpolar } from "@/i18n/mensajes";
import { FilaMetricas } from "khipu-design-system/patrones/metricas";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.empresa.pagina.titulo} · ${messages.app.nombre}` };
}

/** Días entre hoy (Lima) y una fecha `YYYY-MM-DD`; negativo si ya pasó. */
function diasHasta(iso: string): number {
  return diasEntre(hoyLima(), iso);
}

function Punto({ tono }: { tono: "ok" | "aviso" | "error" | "neutro" }) {
  return (
    <span
      className={cn(
        "size-1.5 shrink-0 rounded-full",
        tono === "ok" && "bg-success-solid",
        tono === "aviso" && "bg-warning-solid",
        tono === "error" && "bg-destructive",
        tono === "neutro" && "bg-muted-foreground/50",
      )}
    />
  );
}

function Dato({ etiqueta, children, pendiente }: { etiqueta: string; children?: React.ReactNode; pendiente?: string }) {
  return (
    <div className="min-w-0">
      <span className={cn(ETIQUETA_DATO, "mb-1")}>{etiqueta}</span>
      {pendiente ? (
        // Lo que falta se dice a la vista, no con un «—» y la explicación escondida en un tooltip (C6).
        <span className="inline-flex h-control w-full items-center rounded-lg border border-dashed border-border bg-muted/50 px-3 text-control text-muted-foreground">
          {pendiente}
        </span>
      ) : (
        <span className="inline-flex h-control w-full items-center rounded-lg border border-border bg-muted px-3 font-mono text-control font-medium text-foreground">
          {children}
        </span>
      )}
    </div>
  );
}

function Pill({ tono, children }: { tono: "ok" | "aviso" | "error" | "neutro"; children: React.ReactNode }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-mono text-etiqueta font-medium whitespace-nowrap",
        tono === "ok" && "border-success-border bg-success text-success-foreground",
        tono === "aviso" && "border-warning-border bg-warning text-warning-foreground",
        tono === "error" && "border-destructive/30 bg-destructive-suave text-destructive-texto",
        tono === "neutro" && "border-border bg-secondary text-muted-foreground",
      )}
    >
      <Punto tono={tono} />
      {children}
    </span>
  );
}

export default async function EmpresaPage({ searchParams }: { searchParams: Promise<{ seccion?: string }> }) {
  const { access, empresaId } = await getServerSession();
  const messages = await getMessages();
  const locale = await getLocale();
  const t = messages.empresa.pagina;
  const tm = t.metricas;
  if (!access) redirect("/login");
  if (!empresaId) redirect("/onboarding");

  const [empresa, empresas, personalizacion, envioAlCliente, series, { seccion }] = await Promise.all([
    obtenerEmpresaActual(access, empresaId),
    listarEmpresas(access),
    obtenerPersonalizacionPdf(access, empresaId),
    // #325: si no carga, la pestaña lo dice y el resto de la página sigue.
    obtenerPreferenciaDeEnvioAlCliente(access, empresaId).catch(() => null),
    // Solo alimenta la guía: si no carga, la página sigue y la guía no afirma nada de las series.
    listarSeries(access, empresaId).catch(() => null),
    searchParams,
  ]);
  const pendientes = seccionesPendientes(empresa, hoyLima());

  const beta = empresa.entorno === "BETA";
  const rechazoSol = empresa.credenciales_sol_rechazadas ?? null;
  const domicilio = empresa.domicilio ?? null;
  const vigencia = empresa.certificado_vigencia_hasta;
  const dias = vigencia ? diasHasta(vigencia) : null;
  // Si hay certificado lo dice `tiene_certificado`, no la fecha: uno sin fecha de vigencia está cargado y firma.
  const ec = t.estadoCertificado;
  const estadoCert: {
    tono: "ok" | "aviso" | "error" | "neutro";
    texto: string;
  } = !empresa.tiene_certificado
    ? { tono: "neutro", texto: ec.sinCertificado }
    : dias === null
      ? { tono: "ok", texto: ec.cargado }
      : dias < 0
        ? { tono: "error", texto: ec.vencido }
        : dias <= 30
          ? { tono: "aviso", texto: ec.porVencer }
          : { tono: "ok", texto: ec.vigente };

  return (
    <div className="mx-auto grid w-full max-w-[1520px] min-w-0 grid-cols-1 gap-3">
      <FilaMetricas>
        <Metrica
          etiqueta={tm.estadoCertificado} icono={CertificateIcon} tono={estadoCert.tono}
          ayuda={
            vigencia ? (
              <>
                <span className="truncate">{interpolar(tm.venceEl, { fecha: formatearFecha(vigencia, locale) })}</span>
                <span className="text-muted-foreground/40">·</span>
                <span className={cn("shrink-0 font-mono", dias !== null && dias < 0 && "text-destructive")}>
                  {dias !== null && dias < 0 ? interpolar(tm.haceDias, { dias: Math.abs(dias) }) : interpolar(tm.diasRestantes, { dias: String(dias) })}
                </span>
              </>
            ) : empresa.tiene_certificado ? (
              tm.sinFechaDeVigencia
            ) : (
              tm.cargaTuP12
            )
          }
        >
          <span
            className={cn(
              estadoCert.tono === "ok" && "text-success-foreground",
              estadoCert.tono === "aviso" && "text-warning-foreground",
              estadoCert.tono === "error" && "text-destructive",
              estadoCert.tono === "neutro" && "text-muted-foreground/60",
            )}
          >
            {estadoCert.texto.toUpperCase()}
          </span>
          <span className="text-control font-normal text-muted-foreground">{tm.pkcs12}</span>
        </Metrica>

        <Metrica
          etiqueta={tm.credencialesSol} icono={KeyIcon} tono={rechazoSol ? "error" : empresa.tiene_credenciales_sol ? "ok" : "aviso"}
          ayuda={
            rechazoSol ? (
              <span className="flex items-center gap-1 text-destructive">
                <Punto tono="error" />
                {tm.solNoAceptadas}
              </span>
            ) : (
              <span className={cn("flex items-center gap-1", empresa.tiene_credenciales_sol ? "text-success-foreground" : "text-warning-foreground")}>
                <Punto tono={empresa.tiene_credenciales_sol ? "ok" : "aviso"} />
                {empresa.tiene_credenciales_sol ? tm.solConfiguradas : tm.solPendientes}
              </span>
            )
          }
        >
          <span className={rechazoSol ? "text-destructive" : empresa.tiene_credenciales_sol ? "text-primary" : "text-muted-foreground/60"}>
            {rechazoSol ? tm.solRechazadas : empresa.tiene_credenciales_sol ? tm.solConfiguradasCorto : "—"}
          </span>
        </Metrica>

        <Metrica
          etiqueta={messages.empresa.comun.entornoSunat} icono={CloudIcon} tono={beta ? "aviso" : "ok"}
          ayuda={
            <>
              <span className="truncate">{interpolar(tm.rucNumero, { ruc: empresa.ruc })}</span>
              <span className="text-muted-foreground/40">·</span>
              <span className="shrink-0 text-primary">{messages.nav.ubl}</span>
            </>
          }
        >
          <span className={beta ? "text-warning-foreground" : "text-foreground"}>{beta ? tm.beta : tm.produccion}</span>
          <span className="text-control font-normal text-muted-foreground">{beta ? tm.homologacion : tm.validezTributaria}</span>
        </Metrica>

        <Metrica
          etiqueta={tm.empresasEnLaCuenta} icono={BuildingsIcon} tono="acento"
          ayuda={
            <>
              {/* «Con certificado y SOL», no «listas para emitir»: el listado no trae la vigencia del certificado, y uno vencido no emite (264-H4). */}
              <span className="truncate">{interpolar(tm.conCertificadoYSol, { n: empresas.filter((e) => e.tiene_certificado && e.tiene_credenciales_sol).length })}</span>
            </>
          }
        >
          {empresas.length}
          <span className="text-control font-normal text-muted-foreground">{empresas.length === 1 ? tm.registrada : tm.registradas}</span>
        </Metrica>
      </FilaMetricas>

      <GuiaParaEmitir pasos={pasosParaEmitir(empresa, series, hoyLima(), messages)} />

      {/* #276: una pestaña por tema. La franja de arriba queda siempre a la vista: es el resumen de lo que falta. */}
      <SeccionesEmpresa
        inicial={seccionInicial(seccion, empresa, hoyLima())}
        pendientes={pendientes}
        empresas={empresas.length}
        paneles={{
          datos: (
          <section className={cn(TARJETA, "p-5")}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <BuildingsIcon weight={PESO_ICONO} className="size-4" />
                {t.datos.titulo}
              </div>
            </div>
            <div className="grid grid-cols-1 gap-4 pt-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <Dato etiqueta={t.datos.rucRegistrado}>
                  {empresa.ruc}
                  <SealCheckIcon weight={PESO_ICONO} className="ml-auto size-4 text-success-solid" aria-label={t.datos.rucValidado} />
                </Dato>
              </div>
              <div className="sm:col-span-2">
                <Dato etiqueta={messages.empresa.comun.razonSocial}>{empresa.razon_social}</Dato>
              </div>
              <div className="sm:col-span-2">
                {domicilio ? (
                  <Dato etiqueta={t.datos.domicilioRegistrado}>
                    <span className="truncate font-sans" data-testid="domicilio-actual">
                      {domicilio.direccion}
                      {domicilio.urbanizacion ? `, ${domicilio.urbanizacion}` : ""} · {domicilio.distrito}, {domicilio.provincia}, {domicilio.departamento} ({domicilio.ubigeo})
                    </span>
                  </Dato>
                ) : (
                  <Dato etiqueta={t.datos.domicilioRegistrado} pendiente={t.datos.domicilioPendiente} />
                )}
              </div>
            </div>
            <div className="mt-4 border-t border-border/60 pt-4">
              <p className="mb-3 text-control leading-relaxed text-muted-foreground">
                {t.datos.ayuda}
              </p>
              <FormulariosDeEscritura permiso="configurar">
              <DatosFiscalesForm
                domicilio={domicilio}
                cuentaDetracciones={empresa.cuenta_detracciones ?? null}
                nombreComercial={empresa.nombre_comercial ?? null}
                padronTasaEspecialIgv={empresa.padron_tasa_especial_igv ?? false}
              />
              </FormulariosDeEscritura>
            </div>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-border/60 pt-3 font-mono text-etiqueta text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                <CloudIcon weight={PESO_ICONO} className="size-3.5" />
                {t.datos.envioDirecto}
              </span>
              <span title={t.datos.codigoLocalAyuda}>
                {interpolar(t.datos.codigoLocal, { codigo: domicilio?.codigo_establecimiento ?? "—" })}
              </span>
            </div>
          </section>
          ),
          sol: (
          <div className="flex flex-col gap-4">
          <section className={cn(TARJETA, "p-5 lg:max-w-4xl")}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <KeyIcon weight={PESO_ICONO} className="size-4" />
                {t.sol.titulo}
              </div>
              {rechazoSol ? (
                <Pill tono="error">{t.sol.rechazadas}</Pill>
              ) : (
                <Pill tono={empresa.tiene_credenciales_sol ? "ok" : "aviso"}>{empresa.tiene_credenciales_sol ? t.sol.configuradas : t.sol.pendientes}</Pill>
              )}
            </div>
            {/* #107: SUNAT no acepta las credenciales y los envíos de la empresa quedaron esperando; se dice por qué y que se reanudan solos. */}
            {rechazoSol ? (
              <div className="mt-4" data-testid="credenciales-sol-rechazadas">
                <Alerta tono="error" titulo={t.sol.rechazoTitulo}>
                  {interpolar(t.sol.rechazoTexto, { desde: formatearFechaHora(rechazoSol.desde, locale), motivo: rechazoSol.motivo })}
                </Alerta>
              </div>
            ) : null}
            <div className="mt-4 flex items-start gap-2.5 rounded-lg border border-accent-border bg-accent/60 p-3">
              <ShieldWarningIcon weight={PESO_ICONO} className="mt-0.5 size-4 shrink-0 text-accent-foreground" />
              <p className="text-control leading-relaxed text-accent-foreground">
                {t.sol.secundarioAntes} <strong className="font-semibold">{t.sol.secundario}</strong> {t.sol.secundarioDespues}
              </p>
            </div>
            <div className="mt-4">
              <FormulariosDeEscritura permiso="configurar">
              <CredencialesSolForm configuradas={empresa.tiene_credenciales_sol} />
              </FormulariosDeEscritura>
            </div>
          </section>
          {/* #318: opcionales. Sirven para consultar la validez por la API REST de SUNAT (también en beta), no para emitir. */}
          <section className={cn(TARJETA, "p-5 lg:max-w-4xl")} data-testid="credenciales-api-sunat">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <KeyIcon weight={PESO_ICONO} className="size-4" />
                {t.api.titulo}
              </div>
              <Pill tono={empresa.tiene_credenciales_api_sunat ? "ok" : "neutro"}>{empresa.tiene_credenciales_api_sunat ? t.sol.configuradas : t.api.opcionales}</Pill>
            </div>
            <p className="mt-4 text-control leading-relaxed text-muted-foreground">
              {t.api.texto}
            </p>
            <div className="mt-4">
              <FormulariosDeEscritura permiso="configurar">
              <CredencialesApiSunatForm configuradas={empresa.tiene_credenciales_api_sunat ?? false} />
              </FormulariosDeEscritura>
            </div>
          </section>
          </div>
          ),
          empresas: (
          <section className={cn(TARJETA, "p-5 lg:max-w-4xl")}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <BuildingsIcon weight={PESO_ICONO} className="size-4" />
                {t.empresas.titulo}
              </div>
              <span className="font-mono text-etiqueta text-muted-foreground">
                {interpolar(empresas.length === 1 ? t.empresas.uno : t.empresas.varios, { n: empresas.length })}
              </span>
            </div>
            <p className="mt-3 text-control leading-relaxed text-muted-foreground">
              {t.empresas.texto}
            </p>
            <ul className="mt-3 divide-y divide-border/60 overflow-hidden rounded-lg border border-border/80">
              {empresas.map((e) => {
                const activa = e.id === empresaId;
                return (
                  <li key={e.id} className={cn("flex items-center gap-3 px-3 py-2.5", activa && "bg-accent/40")}>
                    <div
                      className={cn(
                        "flex size-8 shrink-0 items-center justify-center rounded-md font-mono text-etiqueta font-semibold",
                        activa ? "bg-primary text-primary-foreground" : "bg-secondary text-muted-foreground",
                      )}
                    >
                      {e.razon_social.slice(0, 2).toUpperCase()}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-control font-medium text-foreground">{e.razon_social}</p>
                      <p className="font-mono text-etiqueta text-muted-foreground">
                        {interpolar(tm.rucNumero, { ruc: e.ruc })} · {e.entorno === "BETA" ? t.empresas.beta : t.empresas.produccion}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1.5">
                      <span title={e.tiene_certificado ? t.empresas.certificadoCargado : t.empresas.sinCertificado}>
                        <FileLockIcon weight={PESO_ICONO} className={cn("size-4", e.tiene_certificado ? "text-success-solid" : "text-muted-foreground/40")} />
                      </span>
                      <span title={e.tiene_credenciales_sol ? t.empresas.solConfiguradas : t.empresas.sinSol}>
                        <KeyIcon weight={PESO_ICONO} className={cn("size-4", e.tiene_credenciales_sol ? "text-success-solid" : "text-muted-foreground/40")} />
                      </span>
                      {activa ? (
                        <span className="ml-1 inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 font-mono text-etiqueta font-medium text-primary">
                          <CheckIcon weight="bold" className="size-3" /> {t.empresas.activa}
                        </span>
                      ) : null}
                    </div>
                  </li>
                );
              })}
            </ul>
            <div className="mt-3 flex justify-end">
              <NuevaEmpresaDialog
                etiqueta={t.empresas.registrar}
                className={ACCION_SECUNDARIA}
              />
            </div>
          </section>
          ),
          certificado: (
          <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-12">
          <section className={cn(TARJETA, "p-5 lg:col-span-7")}>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <FileLockIcon weight={PESO_ICONO} className="size-4" />
                {t.certificado.titulo}
              </div>
              <Punto tono={estadoCert.tono} />
            </div>
            <dl className="mt-4 grid grid-cols-1 gap-2 rounded-lg border border-border/80 bg-muted p-3.5 font-mono text-control">
              <div className="flex items-center justify-between gap-3" title={t.certificado.archivoAyuda}>
                <dt className="text-muted-foreground">{t.certificado.archivoCargado}</dt>
                <dd className={empresa.tiene_certificado ? "text-foreground" : "text-muted-foreground"}>{empresa.tiene_certificado ? t.certificado.siCifrado : t.certificado.no}</dd>
              </div>
              <div className="flex items-center justify-between gap-3">
                <dt className="text-muted-foreground">{t.certificado.fechaExpiracion}</dt>
                <dd className={cn("font-medium", dias !== null && dias < 0 ? "text-destructive" : "text-foreground")}>
                  {vigencia ? formatearFecha(vigencia, locale) : empresa.tiene_certificado ? t.certificado.sinFecha : t.certificado.noAplica}
                </dd>
              </div>
            </dl>
            <div className="mt-4">
              <FormulariosDeEscritura permiso="configurar">
              <CertificadoForm tieneCertificado={empresa.tiene_certificado} />
              </FormulariosDeEscritura>
            </div>
          </section>

          <section className={cn(TARJETA, "p-5 lg:col-span-5")}>
            <div className={cn(TITULO_SECCION, "border-b border-border/60 pb-3")}>{t.recursos.titulo}</div>
            <ul className="mt-3 grid grid-cols-1 gap-2 text-control">
              <li>
                <a
                  href="https://cpe.sunat.gob.pe/"
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-2.5 rounded-lg border border-border/60 px-3 py-2 text-foreground/90 transition-colors hover:bg-muted"
                >
                  <QuestionIcon weight={PESO_ICONO} className="size-4 shrink-0 text-primary" />
                  <span className="min-w-0 flex-1">{t.recursos.portalCpe}</span>
                  <ArrowSquareOutIcon weight={PESO_ICONO} className="size-3.5 shrink-0 text-muted-foreground" />
                </a>
              </li>
              <li>
                <a
                  href="https://www.gob.pe/indecopi"
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-2.5 rounded-lg border border-border/60 px-3 py-2 text-foreground/90 transition-colors hover:bg-muted"
                >
                  <SealCheckIcon weight={PESO_ICONO} className="size-4 shrink-0 text-primary" />
                  <span className="min-w-0 flex-1">{t.recursos.indecopi}</span>
                  <ArrowSquareOutIcon weight={PESO_ICONO} className="size-3.5 shrink-0 text-muted-foreground" />
                </a>
              </li>
            </ul>
          </section>
          </div>
          ),
          pdf: (
      <section className={cn(TARJETA, "p-5")} id="personalizacion">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
          <div className={TITULO_SECCION}>
            <PaletteIcon weight={PESO_ICONO} className="size-4" />
            {t.pdf.titulo}
          </div>
          <span className="text-control text-muted-foreground">{t.pdf.ayuda}</span>
        </div>
        <div className="mt-4">
          <FormulariosDeEscritura permiso="configurar">
          <PersonalizacionPdfForm inicial={personalizacion} />
          </FormulariosDeEscritura>
        </div>
      </section>
          ),
          correo: (
          <section className={cn(TARJETA, "p-5 lg:max-w-4xl")} data-testid="seccion-envio-al-cliente">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
              <div className={TITULO_SECCION}>
                <EnvelopeSimpleIcon weight={PESO_ICONO} className="size-4" />
                {t.correo.titulo}
              </div>
              {envioAlCliente ? (
                <Pill tono={envioAlCliente.enviar_automaticamente ? "ok" : "neutro"}>{envioAlCliente.enviar_automaticamente ? t.correo.activado : t.correo.desactivado}</Pill>
              ) : null}
            </div>
            <div className="mt-4">
              {envioAlCliente ? (
                <FormulariosDeEscritura permiso="configurar">
                  <EnvioAlClienteForm inicial={envioAlCliente} />
                </FormulariosDeEscritura>
              ) : (
                <Alerta tono="error" titulo={t.correo.errorTitulo}>
                  {t.correo.errorTexto}
                </Alerta>
              )}
            </div>
          </section>
          ),
        }}
      />
    </div>
  );
}
