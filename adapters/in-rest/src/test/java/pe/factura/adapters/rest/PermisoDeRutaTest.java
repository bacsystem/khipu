package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.domain.cuenta.Permiso;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.domain.cuenta.Permiso.*;

/**
 * #21: cada endpoint del portal tiene decidido qué permiso pide. Recorre los controladores reales: un endpoint nuevo que no está en {@link #ESPERADO} hace fallar la prueba
 * hasta que alguien decida a qué rol le corresponde (y lo agregue aquí). Sin esto, uno nuevo de emisión quedaría reservado al administrador sin que nadie lo note, o uno de
 * configuración podría colarse en la lista de emisión.
 */
class PermisoDeRutaTest {
    static final Map<String, Permiso> ESPERADO = Map.ofEntries(
            // Comprobantes: leer es de todos; emitir y lo que se hace con uno emitido, del emisor.
            entry("GET /v1/facturas", LEER), entry("GET /v1/facturas/x", LEER), entry("GET /v1/facturas/descarga", LEER), entry("GET /v1/facturas/resumen", LEER),
            entry("GET /v1/facturas/x/xml", LEER), entry("GET /v1/facturas/x/pdf", LEER), entry("GET /v1/facturas/x/cdr", LEER), entry("GET /v1/facturas/x/bajas", LEER),
            entry("GET /v1/bajas/x", LEER),
            entry("POST /v1/facturas", EMITIR), entry("POST /v1/facturas/calcular", EMITIR), entry("POST /v1/facturas/x/enviar", EMITIR),
            entry("POST /v1/facturas/x/correo", EMITIR), entry("POST /v1/facturas/x/cdr/recuperar", EMITIR), entry("POST /v1/facturas/x/baja", EMITIR),
            entry("POST /v1/notas", EMITIR),
            // Clientes y productos guardados: con lo que se emite.
            entry("GET /v1/clientes", LEER), entry("GET /v1/clientes/x", LEER), entry("POST /v1/clientes", EMITIR), entry("PUT /v1/clientes/x", EMITIR),
            entry("PUT /v1/clientes/documento/x/x", EMITIR), entry("DELETE /v1/clientes/x", EMITIR),
            entry("GET /v1/productos", LEER), entry("GET /v1/productos/x", LEER), entry("POST /v1/productos", EMITIR), entry("PUT /v1/productos/x", EMITIR),
            entry("DELETE /v1/productos/x", EMITIR),
            // Consultas a SUNAT: leer.
            entry("GET /v1/consultas/ruc/x", LEER), entry("GET /v1/consultas/validez", LEER),
            // Empresa, series, certificado, SOL, establecimientos, API keys, PDF: configuración.
            entry("GET /v1/empresa", LEER), entry("GET /v1/series", LEER), entry("GET /v1/empresa/establecimientos", LEER), entry("GET /v1/empresas", LEER),
            entry("GET /v1/empresa/personalizacion-pdf", LEER), entry("GET /v1/empresa/personalizacion-pdf/vista-previa", LEER), entry("GET /v1/empresa/logo", LEER),
            entry("POST /v1/empresas", CONFIGURAR), entry("POST /v1/empresa/certificado", CONFIGURAR), entry("PUT /v1/empresa/datos-fiscales", CONFIGURAR),
            entry("PUT /v1/empresa/credenciales-sol", CONFIGURAR), entry("POST /v1/series", CONFIGURAR), entry("POST /v1/empresa/establecimientos", CONFIGURAR),
            entry("PUT /v1/empresa/establecimientos/x", CONFIGURAR), entry("DELETE /v1/empresa/establecimientos/x", CONFIGURAR),
            entry("PUT /v1/empresa/personalizacion-pdf", CONFIGURAR), entry("PUT /v1/empresa/logo", CONFIGURAR), entry("DELETE /v1/empresa/logo", CONFIGURAR),
            entry("GET /v1/empresa/api-keys", CONFIGURAR), entry("POST /v1/empresa/api-keys", CONFIGURAR), entry("DELETE /v1/empresa/api-keys/x", CONFIGURAR),
            // Webhooks: mirarlos es de todos; cambiarlos, configuración.
            entry("GET /v1/webhooks", LEER), entry("GET /v1/webhooks/eventos", LEER), entry("GET /v1/webhooks/x/entregas", LEER),
            entry("POST /v1/webhooks", CONFIGURAR), entry("PATCH /v1/webhooks/x", CONFIGURAR), entry("DELETE /v1/webhooks/x", CONFIGURAR),
            entry("POST /v1/webhooks/x/secreto", CONFIGURAR), entry("POST /v1/webhooks/x/prueba", CONFIGURAR), entry("POST /v1/webhooks/entregas/x/reenviar", CONFIGURAR),
            // La cuenta: plan, consumo y accesos de soporte se miran; el equipo es del administrador.
            entry("GET /v1/cuenta", LEER), entry("GET /v1/cuenta/accesos-de-soporte", LEER),
            entry("GET /v1/cuenta/usuarios", GESTIONAR_EQUIPO), entry("PUT /v1/cuenta/usuarios/x/rol", GESTIONAR_EQUIPO),
            entry("POST /v1/cuenta/usuarios/x/desactivar", GESTIONAR_EQUIPO), entry("POST /v1/cuenta/usuarios/x/reactivar", GESTIONAR_EQUIPO),
            entry("GET /v1/cuenta/invitaciones", GESTIONAR_EQUIPO), entry("POST /v1/cuenta/invitaciones", GESTIONAR_EQUIPO),
            entry("POST /v1/cuenta/invitaciones/x/reenviar", GESTIONAR_EQUIPO), entry("DELETE /v1/cuenta/invitaciones/x", GESTIONAR_EQUIPO));

    /** Los endpoints con sesión de cliente que existen: fuera los del backoffice, los de la propia sesión y los públicos. */
    static Map<String, Permiso> reales() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, Permiso> reales = new TreeMap<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("pe.factura.adapters.rest")) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class);
            String prefijo = base == null || base.value().length == 0 ? "" : base.value()[0];
            for (Method m : c.getDeclaredMethods()) {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (rm == null) continue;
                String[] rutas = rm.value().length == 0 ? new String[]{""} : rm.value();
                for (String r : rutas) {
                    String ruta = (prefijo + r).replaceAll("\\{[^}]+}", "x");
                    if (RutaRequest.esAdmin(ruta) || ruta.startsWith("/v1/auth/") || RutaRequest.esPublica(ruta)) continue;
                    for (var metodo : rm.method()) reales.put(metodo.name() + " " + ruta, PermisoDeRuta.requerido(metodo.name(), ruta));
                }
            }
        }
        return reales;
    }

    @Test void cadaEndpointDelPortalTieneSuPermisoDecidido() throws Exception {
        Map<String, Permiso> reales = reales();
        assertThat(reales).as("se encontraron los endpoints de los controladores").hasSizeGreaterThan(50);
        assertThat(reales.keySet()).as("endpoints sin decidir: agréguelos a ESPERADO con el permiso que les corresponde").isSubsetOf(ESPERADO.keySet());
        assertThat(reales).as("permiso de cada endpoint").isEqualTo(new TreeMap<>(ESPERADO));
    }

    @Test void unaEscrituraQueNadieClasificoEsDeAdministrador() {
        assertThat(PermisoDeRuta.requerido("POST", "/v1/algo-nuevo")).isEqualTo(CONFIGURAR);
        assertThat(PermisoDeRuta.requerido("DELETE", "/v1/facturas/1")).isEqualTo(CONFIGURAR);
        assertThat(PermisoDeRuta.requerido("GET", "/v1/algo-nuevo")).isEqualTo(LEER);
    }
}
