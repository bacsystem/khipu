<#ftl output_format="XHTML" strip_whitespace=true>
<#import "comun.ftl" as u>
<#--
  Guía de remisión electrónica (#39), remitente o transportista. Reusa la página de comun.ftl (cabecera, diseño y logo de la empresa) con c = la guía.
  El modelo trae además tr (traslado), remitente / transportista (los datos de cada tipo), fechaEmision, fechaInicio, titulo y qr (data URI o ausente:
  el QR codifica la URL de consulta que SUNAT pone en el CDR y no existe antes de que SUNAT responda).
-->
<#macro punto p>${p.direccion()} · ubigeo ${p.ubigeo()}<#if p.codigoEstablecimiento()??> · establecimiento ${p.codigoEstablecimiento()} (RUC ${p.rucEstablecimiento()})</#if></#macro>
<@u.pagina titulo=titulo>
  <div class="bloque">
    <table class="datos">
      <tr>
        <td class="k">Fecha de emisión</td><td>${fechaEmision}</td>
        <td class="k">Inicio del traslado</td><td>${fechaInicio}</td>
      </tr>
      <#if remitente??>
      <tr>
        <td class="k">Motivo</td><td>${remitente.motivo()} - ${remitente.descripcionMotivo()}</td>
        <td class="k">Modalidad</td><td>${remitente.modalidad().codigo()} - ${u.desc("18", remitente.modalidad().codigo())}</td>
      </tr>
      </#if>
      <tr>
        <td class="k">Peso bruto total</td><td>${tr.pesoBruto()?string("#,##0.###")} ${tr.unidadPeso()}</td>
        <td class="k">Estado</td><td>${c.estado()}</td>
      </tr>
    </table>
  </div>
  <div class="bloque">
    <h2>Destinatario</h2>
    <table class="datos">
      <tr><td class="k">${u.desc("06", c.destinatario().tipoDoc())}</td><td>${c.destinatario().numDoc()}</td></tr>
      <tr><td class="k">Nombre</td><td>${c.destinatario().nombre()}</td></tr>
    </table>
  </div>
  <#if transportista??>
  <div class="bloque">
    <h2>Remitente</h2>
    <table class="datos">
      <tr><td class="k">${u.desc("06", transportista.remitente().tipoDoc())}</td><td>${transportista.remitente().numDoc()}</td></tr>
      <tr><td class="k">Nombre</td><td>${transportista.remitente().nombre()}</td></tr>
      <tr><td class="k">Registro MTC del transportista</td><td>${transportista.registroMtc()}</td></tr>
    </table>
  </div>
  </#if>
  <div class="bloque">
    <h2>Traslado</h2>
    <table class="datos">
      <tr><td class="k">Punto de partida</td><td><@punto p=tr.partida()/></td></tr>
      <tr><td class="k">Punto de llegada</td><td><@punto p=tr.llegada()/></td></tr>
      <#if remitente?? && remitente.transportista()??>
      <tr><td class="k">Transportista</td><td>RUC ${remitente.transportista().ruc()} · ${remitente.transportista().razonSocial()}<#if remitente.transportista().registroMtc()??> · MTC ${remitente.transportista().registroMtc()}</#if></td></tr>
      </#if>
      <#list tr.vehiculos() as v>
      <tr><td class="k">Vehículo ${(v?index == 0)?then("principal", "secundario")}</td><td>Placa ${v.placa()}</td></tr>
      </#list>
      <#list tr.conductores() as k>
      <tr><td class="k">Conductor ${(k?index == 0)?then("principal", "secundario")}</td><td>${k.nombres()} ${k.apellidos()} · ${u.desc("06", k.tipoDoc())} ${k.numDoc()} · licencia ${k.licencia()}</td></tr>
      </#list>
      <#list indicadores as i>
      <tr><td class="k">Indicador</td><td>${i}</td></tr>
      </#list>
    </table>
  </div>
  <table class="lineas">
    <thead>
      <tr>
        <th class="c" style="width: 10mm;">N.º</th>
        <th style="width: 22mm;">Código</th>
        <th>Descripción</th>
        <th class="c" style="width: 14mm;">Und.</th>
        <th class="n" style="width: 24mm;">Cantidad</th>
      </tr>
    </thead>
    <tbody>
      <#list c.items() as it>
      <tr>
        <td class="c">${it?index + 1}</td>
        <td>${it.codigo()!""}</td>
        <td>${it.descripcion()}</td>
        <td class="c">${it.unidad()}</td>
        <td class="n">${it.cantidad()?string("#,##0.##########")}</td>
      </tr>
      </#list>
    </tbody>
  </table>
  <#if c.documentos()?has_content>
  <div class="bloque" style="margin-top: 3mm;">
    <h2>Documentos relacionados</h2>
    <table class="datos">
      <#list c.documentos() as d><tr><td class="k">${d.descripcionTipo()}</td><td>${d.numero()}<#if d.rucEmisor()??> (RUC ${d.rucEmisor()})</#if></td></tr></#list>
    </table>
  </div>
  </#if>
  <#if c.observaciones()??>
  <div class="bloque observaciones"><h2>Observaciones</h2><p>${c.observaciones()}</p></div>
  </#if>
  <table class="pie">
    <tr>
      <#if qr??><td class="qr" style="width: 36mm;"><img src="${qr}" alt="QR" /></td></#if>
      <td>
        <#if p.pieDePagina()??>
        <p class="leyenda">${p.pieDePagina()}</p>
        <#else>
        <p class="leyenda">Representación impresa de la ${titulo} emitida por ${t.razonSocial()} (RUC ${t.ruc()}) a través de khipu.</p>
        </#if>
        <#if c.enlaceConsulta()??><p class="leyenda">Consulte la guía en SUNAT: ${c.enlaceConsulta()}</p>
        <#else><p class="leyenda">SUNAT aún no devolvió la constancia (CDR) de esta guía: el código QR de consulta aparece cuando la devuelve.</p></#if>
        <#if c.hash()??><p class="leyenda">Valor resumen (hash): <span class="hash">${c.hash()}</span></p></#if>
      </td>
    </tr>
  </table>
</@u.pagina>
