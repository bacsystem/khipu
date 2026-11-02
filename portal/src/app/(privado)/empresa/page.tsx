import { FormulariosDeEscritura } from "@/lib/solo-lectura";
import { ArrowSquareOutIcon, BookOpenIcon, BuildingsIcon, CertificateIcon, CheckIcon, CloudIcon, EnvelopeSimpleIcon, FileLockIcon, KeyIcon, PaletteIcon, QuestionIcon, SealCheckIcon, ShieldWarningIcon, SlidersHorizontalIcon } from "@phosphor-icons/react/dist/ssr";
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
import { listarEmpresas, obtenerEmpresaActual, obtenerPersonalizacionPdf, obtenerPreferenciaDeEnvioAlCliente, obtenerPreferenciasDeEmision } from "@/lib/api/empresas";
import { EnvioAlClienteForm } from "@/components/empresa/envio-al-cliente-form";
import { PreferenciasEmisionForm } from "@/components/empresa/preferencias-emision-form";
import { listarSeries } from "@/lib/api/series";
import { pasosParaEmitir } from "@/lib/empresa/guia-emision";
import { seccionesPendientes, seccionInicial } from "@/lib/empresa/secciones";
import { ACCION_SECUNDARIA, CONTENEDOR_PAGINA, TARJETA } from "@/lib/estilos";
import { Alerta } from "khipu-design-system/feedback/alerta";
import { diasEntre, formatearFecha, formatearFechaHora, hoyLima } from "@/lib/formato";
import { getServerSession } from "@/lib/session-server";
import { cn } from "@/lib/utils";
import { Metrica } from "@/components/compartidos/metrica";
import { interpolar } from "@/i18n/mensajes";
import { AyudaMetrica, FilaMetricas, Punto } from "khipu-design-system/patrones/metricas";
import { CabeceraSeccion, PieSeccion } from "khipu-design-system/patrones/cabecera-seccion";
import { Chip, PillEstado } from "khipu-design-system/patrones/pill-estado";
import { ListaDatos } from "khipu-design-system/datos/lista-datos";

export async function generateMetadata(): Promise<Metadata> {
  const messages = await getMessages();
  return { title: `${messages.empresa.pagina.titulo} · ${messages.app.nombre}` };
}

/** Días entre hoy (Lima) y una fecha `YYYY-MM-DD`; negativo si ya pasó. */
function diasHasta(iso: string): number {
  return diasEntre(hoyLima(), iso);
}

export default async function EmpresaPage({ searchParams }: { searchParams: Promise<{ seccion?: string }> }) {
  const { access, empresaId } = await getServerSession();
  const messages = await getMessages();
  const locale = await getLocale();
  const t = messages.empresa.pagina;
  const tm = t.metricas;
  if (!access) redirect("/login");
  if (!empresaId) redirect("/onboarding");

  const [empresa, empresas, personalizacion, envioAlCliente, preferenciasEmision, series, { seccion }] = await Promise.all([
    obtenerEmpresaActual(access, empresaId),
    listarEmpresas(access),
    obtenerPersonalizacionPdf(access, empresaId),
    // #325: si no carga, la pestaña lo dice y el resto de la página sigue.
    obtenerPreferenciaDeEnvioAlCliente(access, empresaId).catch(() => null),
    // Igual: si las preferencias de emisión no cargan, su pestaña lo dice y el resto sigue.
    obtenerPreferenciasDeEmision(access, empresaId).catch(() => null),
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
    <div className={CONTENEDOR_PAGINA}>
      <FilaMetricas>
        <Metrica
          etiqueta={tm.estadoCertificado} icono={CertificateIcon} tono={estadoCert.tono} texto
          ayuda={
            vigencia ? (
              <AyudaMetrica tono={estadoCert.tono}>
                {interpolar(tm.venceEl, { fecha: formatearFecha(vigencia, locale) })} ·{" "}
                {dias !== null && dias < 0 ? interpolar(tm.haceDias, { dias: Math.abs(dias) }) : interpolar(tm.diasRestantes, { dias: String(dias) })}
              </AyudaMetrica>
            ) : (
              <AyudaMetrica tono={estadoCert.tono}>{empresa.tiene_certificado ? tm.sinFechaDeVigencia : tm.cargaTuP12}</AyudaMetrica>
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
            {estadoCert.texto}
          </span>
          <span className="text-control font-normal text-muted-foreground">{tm.pkcs12}</span>
        </Metrica>

        <Metrica
          etiqueta={tm.credencialesSol} icono={KeyIcon} tono={rechazoSol ? "error" : empresa.tiene_credenciales_sol ? "ok" : "aviso"} texto
          ayuda={
            rechazoSol ? (
              <AyudaMetrica tono="error">{tm.solNoAceptadas}</AyudaMetrica>
            ) : (
              <AyudaMetrica tono={empresa.tiene_credenciales_sol ? "ok" : "aviso"}>
                {empresa.tiene_credenciales_sol ? tm.solConfiguradas : tm.solPendientes}
              </AyudaMetrica>
            )
          }
        >
          <span className={rechazoSol ? "text-destructive" : empresa.tiene_credenciales_sol ? "text-primary" : "text-muted-foreground/60"}>
            {rechazoSol ? tm.solRechazadas : empresa.tiene_credenciales_sol ? tm.solConfiguradasCorto : "—"}
          </span>
        </Metrica>

        <Metrica
          etiqueta={messages.empresa.comun.entornoSunat} icono={CloudIcon} tono={beta ? "aviso" : "ok"} texto
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
          <section className={TARJETA}>
            <CabeceraSeccion icon={BuildingsIcon} titulo={t.datos.titulo} />
            <div className="px-4 pb-4">
              <ListaDatos
                datos={[
                  {
                    etiqueta: t.datos.rucRegistrado,
                    valor: (
                      <span className="inline-flex items-center gap-1.5">
                        {empresa.ruc}
                        <SealCheckIcon weight={PESO_ICONO} className="size-4 text-success-solid" aria-label={t.datos.rucValidado} />
                      </span>
                    ),
                    mono: true,
                  },
                  { etiqueta: messages.empresa.comun.razonSocial, valor: empresa.razon_social },
                  {
                    etiqueta: t.datos.domicilioRegistrado,
                    // Lo que falta se dice a la vista, no con un «—» y la explicación escondida en un tooltip (C6).
                    valor: domicilio ? (
                      <span data-testid="domicilio-actual">
                        {domicilio.direccion}
                        {domicilio.urbanizacion ? `, ${domicilio.urbanizacion}` : ""} · {domicilio.distrito}, {domicilio.provincia}, {domicilio.departamento} ({domicilio.ubigeo})
                      </span>
                    ) : (
                      <span className="text-muted-foreground">{t.datos.domicilioPendiente}</span>
                    ),
                  },
                ]}
              />
              <div className="mt-4 border-t border-border/60 pt-4">
                <p className="mb-3 text-control leading-relaxed text-muted-foreground">{t.datos.ayuda}</p>
                <FormulariosDeEscritura permiso="configurar">
                  <DatosFiscalesForm
                    domicilio={domicilio}
                    cuentaDetracciones={empresa.cuenta_detracciones ?? null}
                    nombreComercial={empresa.nombre_comercial ?? null}
                    padronTasaEspecialIgv={empresa.padron_tasa_especial_igv ?? false}
                  />
                </FormulariosDeEscritura>
              </div>
            </div>
            <PieSeccion className="justify-between">
              <span className="inline-flex items-center gap-1.5">
                <CloudIcon weight={PESO_ICONO} className="size-3.5" />
                {t.datos.envioDirecto}
              </span>
              <span title={t.datos.codigoLocalAyuda} className="font-mono">
                {interpolar(t.datos.codigoLocal, { codigo: domicilio?.codigo_establecimiento ?? "—" })}
              </span>
            </PieSeccion>
          </section>
          ),
          sol: (
          <div className="flex flex-col gap-4">
          <section className={TARJETA}>
            <CabeceraSeccion
              icon={KeyIcon}
              titulo={t.sol.titulo}
              derecha={
                rechazoSol ? (
                  <PillEstado tono="error">{t.sol.rechazadas}</PillEstado>
                ) : (
                  <PillEstado tono={empresa.tiene_credenciales_sol ? "ok" : "aviso"}>{empresa.tiene_credenciales_sol ? t.sol.configuradas : t.sol.pendientes}</PillEstado>
                )
              }
            />
            <div className="px-4 pb-4">
            {/* #107: SUNAT no acepta las credenciales y los envíos de la empresa quedaron esperando; se dice por qué y que se reanudan solos. */}
            {rechazoSol ? (
              <div className="mb-4" data-testid="credenciales-sol-rechazadas">
                <Alerta tono="error" titulo={t.sol.rechazoTitulo}>
                  {interpolar(t.sol.rechazoTexto, { desde: formatearFechaHora(rechazoSol.desde, locale), motivo: rechazoSol.motivo })}
                </Alerta>
              </div>
            ) : null}
            <Alerta tono="info" icon={ShieldWarningIcon}>
              {t.sol.secundarioAntes} <strong className="font-semibold">{t.sol.secundario}</strong> {t.sol.secundarioDespues}
            </Alerta>
            <div className="mt-4">
              <FormulariosDeEscritura permiso="configurar">
              <CredencialesSolForm configuradas={empresa.tiene_credenciales_sol} />
              </FormulariosDeEscritura>
            </div>
            </div>
          </section>
          {/* #318: opcionales. Sirven para consultar la validez por la API REST de SUNAT (también en beta), no para emitir. */}
          <section className={TARJETA} data-testid="credenciales-api-sunat">
            <CabeceraSeccion
              icon={KeyIcon}
              titulo={t.api.titulo}
              derecha={<PillEstado tono={empresa.tiene_credenciales_api_sunat ? "ok" : "neutro"}>{empresa.tiene_credenciales_api_sunat ? t.sol.configuradas : t.api.opcionales}</PillEstado>}
            />
            <div className="px-4 pb-4">
              <p className="text-control leading-relaxed text-muted-foreground">{t.api.texto}</p>
              <div className="mt-4">
                <FormulariosDeEscritura permiso="configurar">
                <CredencialesApiSunatForm configuradas={empresa.tiene_credenciales_api_sunat ?? false} />
                </FormulariosDeEscritura>
              </div>
            </div>
          </section>
          </div>
          ),
          empresas: (
          <section className={TARJETA}>
            <CabeceraSeccion
              icon={BuildingsIcon}
              titulo={t.empresas.titulo}
              derecha={<Chip>{interpolar(empresas.length === 1 ? t.empresas.uno : t.empresas.varios, { n: empresas.length })}</Chip>}
            />
            <div className="px-4 pb-4">
            <p className="text-control leading-relaxed text-muted-foreground">
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
                        <Chip className="ml-1">
                          <CheckIcon weight="bold" className="size-3" /> {t.empresas.activa}
                        </Chip>
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
            </div>
          </section>
          ),
          certificado: (
          <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-12">
          <section className={cn(TARJETA, "lg:col-span-7")}>
            <CabeceraSeccion icon={FileLockIcon} titulo={t.certificado.titulo} derecha={<Punto tono={estadoCert.tono} />} />
            <div className="px-4 pb-4">
              <ListaDatos
                datos={[
                  {
                    etiqueta: <span title={t.certificado.archivoAyuda}>{t.certificado.archivoCargado}</span>,
                    valor: <span className={empresa.tiene_certificado ? undefined : "text-muted-foreground"}>{empresa.tiene_certificado ? t.certificado.siCifrado : t.certificado.no}</span>,
                    mono: true,
                  },
                  {
                    etiqueta: t.certificado.fechaExpiracion,
                    valor: (
                      <span className={cn("font-medium", dias !== null && dias < 0 && "text-destructive")}>
                        {vigencia ? formatearFecha(vigencia, locale) : empresa.tiene_certificado ? t.certificado.sinFecha : t.certificado.noAplica}
                      </span>
                    ),
                    mono: true,
                  },
                ]}
              />
              <div className="mt-4">
                <FormulariosDeEscritura permiso="configurar">
                <CertificadoForm tieneCertificado={empresa.tiene_certificado} />
                </FormulariosDeEscritura>
              </div>
            </div>
          </section>

          <section className={cn(TARJETA, "lg:col-span-5")}>
            <CabeceraSeccion icon={BookOpenIcon} titulo={t.recursos.titulo} />
            <ul className="grid grid-cols-1 gap-2 px-4 pb-4 text-control">
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
          <section className={TARJETA} id="personalizacion">
            <CabeceraSeccion icon={PaletteIcon} titulo={t.pdf.titulo} subtitulo={t.pdf.ayuda} />
            <div className="px-4 pb-4">
              <FormulariosDeEscritura permiso="configurar">
              <PersonalizacionPdfForm inicial={personalizacion} />
              </FormulariosDeEscritura>
            </div>
          </section>
          ),
          correo: (
          <section className={TARJETA} data-testid="seccion-envio-al-cliente">
            <CabeceraSeccion
              icon={EnvelopeSimpleIcon}
              titulo={t.correo.titulo}
              derecha={
                envioAlCliente ? (
                  <PillEstado tono={envioAlCliente.enviar_automaticamente ? "ok" : "neutro"}>{envioAlCliente.enviar_automaticamente ? t.correo.activado : t.correo.desactivado}</PillEstado>
                ) : null
              }
            />
            <div className="px-4 pb-4">
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
          emision: (
          <section className={TARJETA} data-testid="seccion-preferencias-emision">
            <CabeceraSeccion
              icon={SlidersHorizontalIcon}
              titulo={t.emision.titulo}
              derecha={
                preferenciasEmision ? (
                  <PillEstado tono={preferenciasEmision.opciones_avanzadas ? "ok" : "neutro"}>
                    {preferenciasEmision.opciones_avanzadas ? t.emision.activadas : t.emision.desactivadas}
                  </PillEstado>
                ) : null
              }
            />
            <div className="px-4 pb-4">
              {preferenciasEmision ? (
                <FormulariosDeEscritura permiso="configurar">
                  <PreferenciasEmisionForm inicial={preferenciasEmision} />
                </FormulariosDeEscritura>
              ) : (
                <Alerta tono="error" titulo={t.emision.errorTitulo}>
                  {t.emision.errorTexto}
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
