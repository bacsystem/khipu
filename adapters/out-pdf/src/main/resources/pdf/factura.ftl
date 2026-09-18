<#ftl output_format="XHTML" strip_whitespace=true>
<#import "comun.ftl" as u>
<@u.pagina titulo="FACTURA ELECTRÓNICA">
  <@u.partes />
  <@u.lineas />
  <@u.totales />
  <@u.condiciones />
  <@u.pie tipoNombre="factura" />
</@u.pagina>
