<#ftl output_format="XHTML" strip_whitespace=true>
<#import "comun.ftl" as u>
<#-- Comprobante de retención (20) o de percepción (40), #40: el emisor es el agente; la contraparte, el proveedor al que se retiene o el cliente al que se percibe. No hay líneas
     de venta: una fila por documento pagado o cobrado, con su pago en la moneda del documento y, en soles, lo retenido o percibido y el neto. -->
<#assign a = c.agente()>
<#assign ret = a.esRetencion()>
<@u.pagina titulo=ret?then("COMPROBANTE DE RETENCIÓN ELECTRÓNICO", "COMPROBANTE DE PERCEPCIÓN ELECTRÓNICO")>
  <div class="bloque">
    <h2>${ret?then("Proveedor", "Cliente")}</h2>
    <table class="datos">
      <tr>
        <td class="k">${ret?then("Razón social", "Nombre")}</td><td>${c.receptor().razonSocial()}</td>
        <td class="k">Fecha de emisión</td><td>${fechaEmision}</td>
      </tr>
      <tr>
        <td class="k">Documento</td><td>${u.desc("06", c.receptor().tipoDoc())} ${c.receptor().numDoc()}</td>
        <td class="k">Régimen</td><td>${a.regimen()} - ${a.descripcionRegimen()}</td>
      </tr>
      <tr>
        <td class="k">Dirección</td><td><#if c.receptor().direccion()??>${c.receptor().direccion()}<#else>-</#if></td>
        <td class="k">Tasa</td><td>${a.tasa()?string["0.##"]}%</td>
      </tr>
    </table>
  </div>
  <table class="lineas">
    <thead>
      <tr>
        <th>Tipo</th><th>Número</th><th>Emisión</th><th class="n">Importe total</th><th>${ret?then("Pago", "Cobro")}</th><th>Fecha</th>
        <th class="n">Importe ${ret?then("pagado", "cobrado")}</th><th class="n">T. cambio</th><th class="n">${ret?then("Retenido", "Percibido")} (S/)</th>
        <th class="n">${ret?then("Neto pagado", "Neto cobrado")} (S/)</th>
      </tr>
    </thead>
    <tbody>
      <#list a.documentos() as d>
      <tr>
        <td>${u.desc("01", d.tipo())}</td><td>${d.numero()}</td><td>${d.fechaEmision().toString()}</td>
        <td class="n">${d.moneda()} ${u.m(d.importeTotal())}</td><td>${d.numeroPago()?c}</td><td>${d.fechaPago().toString()}</td>
        <td class="n">${d.moneda()} ${u.m(d.importePago())}</td><td class="n"><#if d.enSoles()>-<#else>${d.tipoCambio()?string["0.000###"]}</#if></td>
        <td class="n">${u.m(d.importeDelAgente(a.tasa()))}</td><td class="n">${u.m(d.neto(c.tipo(), a.tasa()))}</td>
      </tr>
      </#list>
    </tbody>
  </table>
  <table class="totales">
    <tr><td class="k">Importe total ${ret?then("retenido", "percibido")}</td><td class="n">S/ ${u.m(tot.total())}</td></tr>
    <#if tot.tieneRedondeo()><tr><td class="k">Redondeo</td><td class="n">S/ ${u.m(tot.redondeo())}</td></tr></#if>
    <tr class="total"><td class="k">Importe total ${ret?then("pagado", "cobrado")}</td><td class="n">S/ ${u.m(c.importeNetoDeAgente())}</td></tr>
  </table>
  <div class="letras">IMPORTE ${ret?then("RETENIDO", "PERCIBIDO")}: ${montoEnLetras}</div>
  <@u.observaciones />
  <@u.pie tipoNombre=ret?then("comprobante de retención", "comprobante de percepción") de="del" />
</@u.pagina>
