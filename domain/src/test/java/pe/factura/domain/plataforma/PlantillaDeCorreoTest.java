package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Variable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlantillaDeCorreoTest {

    // --- el texto de fábrica ------------------------------------------------------------------------------------------------------------

    @ParameterizedTest @EnumSource(PlantillaDeCorreo.class)
    void elTextoDeFabricaSiempreEsValido(PlantillaDeCorreo p) {
        Texto d = p.defecto();

        assertThat(p.validar(d.asunto(), d.cuerpo())).isEqualTo(d);
    }

    @ParameterizedTest @EnumSource(PlantillaDeCorreo.class)
    void cadaVariableTieneNombreDescripcionYEjemplo(PlantillaDeCorreo p) {
        assertThat(p.variables()).isNotEmpty().allSatisfy(v -> {
            assertThat(v.nombre()).matches("[a-z_]+");
            assertThat(v.descripcion()).isNotBlank();
            assertThat(v.ejemplo()).isNotBlank();
        });
        assertThat(p.variables().stream().map(Variable::nombre)).doesNotHaveDuplicates();
    }

    @ParameterizedTest @EnumSource(PlantillaDeCorreo.class)
    void cadaCorreoDiceQueEsYCuandoSeManda(PlantillaDeCorreo p) {
        assertThat(p.etiqueta()).isNotBlank();
        assertThat(p.cuandoSeManda()).isNotBlank();
    }

    @Test void losCorreosDeAccesoNoSirvenSinSuEnlace() {
        for (PlantillaDeCorreo p : new PlantillaDeCorreo[]{PlantillaDeCorreo.VERIFICACION_CORREO, PlantillaDeCorreo.RECUPERACION_CLAVE, PlantillaDeCorreo.BIENVENIDA})
            assertThat(p.variables()).filteredOn(Variable::indispensable).extracting(Variable::nombre).containsExactly("enlace");
    }

    @Test void losAvisosNoTienenVariablesIndispensables() {
        for (PlantillaDeCorreo p : new PlantillaDeCorreo[]{PlantillaDeCorreo.AVISO_CERTIFICADO_POR_VENCER, PlantillaDeCorreo.AVISO_CERTIFICADO_VENCIDO, PlantillaDeCorreo.AVISO_CREDENCIALES_SOL})
            assertThat(p.variables()).noneMatch(Variable::indispensable);
    }

    @Test void deNombreEncuentraCadaCorreoYNadaMas() {
        for (PlantillaDeCorreo p : PlantillaDeCorreo.values()) assertThat(PlantillaDeCorreo.deNombre(p.name())).contains(p);
        assertThat(PlantillaDeCorreo.deNombre("OTRA")).isEmpty();
        assertThat(PlantillaDeCorreo.deNombre("recuperacion_clave")).isEmpty();
        assertThat(PlantillaDeCorreo.deNombre(null)).isEmpty();
    }

    // --- validar ------------------------------------------------------------------------------------------------------------------------

    private static final PlantillaDeCorreo ACCESO = PlantillaDeCorreo.RECUPERACION_CLAVE;
    private static final PlantillaDeCorreo AVISO = PlantillaDeCorreo.AVISO_CREDENCIALES_SOL;

    private static void rechaza(PlantillaDeCorreo p, String asunto, String cuerpo, String mensaje) {
        assertThatThrownBy(() -> p.validar(asunto, cuerpo)).isInstanceOf(DomainException.class).hasMessageContaining(mensaje).extracting("codigo").isEqualTo("PLANTILLA_INVALIDA");
    }

    @Test void normalizaElAsuntoYElCuerpo() {
        Texto t = ACCESO.validar("  Tu enlace  ", "Abre {enlace}\r\ny listo\r  \n");

        assertThat(t.asunto()).isEqualTo("Tu enlace");
        assertThat(t.cuerpo()).isEqualTo("Abre {enlace}\ny listo");
    }

    @Test void elAsuntoEsObligatorio() {
        rechaza(ACCESO, null, "Abre {enlace}", "asunto no puede estar vacío");
        rechaza(ACCESO, "   ", "Abre {enlace}", "asunto no puede estar vacío");
    }

    @Test void elAsuntoVaEnUnaSolaLineaPorqueEsUnaCabecera() {
        rechaza(ACCESO, "Hola\nBcc: otro@x.pe", "Abre {enlace}", "una sola línea");
        rechaza(ACCESO, "Hola\r\nBcc: otro@x.pe", "Abre {enlace}", "una sola línea");
        rechaza(ACCESO, "Hola\tmundo", "Abre {enlace}", "una sola línea");
    }

    @Test void elAsuntoTieneUnTope() {
        assertThat(ACCESO.validar("a".repeat(PlantillaDeCorreo.MAX_ASUNTO), "Abre {enlace}").asunto()).hasSize(PlantillaDeCorreo.MAX_ASUNTO);
        rechaza(ACCESO, "a".repeat(PlantillaDeCorreo.MAX_ASUNTO + 1), "Abre {enlace}", "hasta 150 caracteres");
    }

    @Test void elTopeDelAsuntoSeCuentaDespuesDeRecortar() {
        assertThat(ACCESO.validar("  " + "a".repeat(PlantillaDeCorreo.MAX_ASUNTO) + "  ", "Abre {enlace}").asunto()).hasSize(PlantillaDeCorreo.MAX_ASUNTO);
    }

    @Test void elCuerpoEsObligatorio() {
        rechaza(AVISO, "Aviso", null, "cuerpo no puede estar vacío");
        rechaza(AVISO, "Aviso", " \n ", "cuerpo no puede estar vacío");
    }

    @Test void elCuerpoTieneUnTopeContadoDespuesDeNormalizar() {
        assertThat(AVISO.validar("Aviso", "a".repeat(PlantillaDeCorreo.MAX_CUERPO)).cuerpo()).hasSize(PlantillaDeCorreo.MAX_CUERPO);
        assertThat(AVISO.validar("Aviso", "a".repeat(PlantillaDeCorreo.MAX_CUERPO) + "   \r\n").cuerpo()).hasSize(PlantillaDeCorreo.MAX_CUERPO);
        rechaza(AVISO, "Aviso", "a".repeat(PlantillaDeCorreo.MAX_CUERPO + 1), "hasta 5000 caracteres");
    }

    @Test void elCuerpoAceptaSaltosDeLineaYTabulacionesPeroNoOtrosControles() {
        assertThat(AVISO.validar("Aviso", "Hola\n\tdos\nlíneas").cuerpo()).isEqualTo("Hola\n\tdos\nlíneas");
        rechaza(AVISO, "Aviso", "Hola\u0000mundo", "caracteres de control");
        rechaza(AVISO, "Aviso", "Hola\u001bmundo", "caracteres de control");
    }

    @Test void unaVariableQueElCorreoNoTieneSeRechazaDiciendoCualesAdmite() {
        rechaza(AVISO, "Aviso de {empresa}", "Hola", "{empresa}");
        rechaza(AVISO, "Aviso", "Hola {fecha}", "{fecha}");
        rechaza(AVISO, "Aviso", "Hola {fecha} y {cuando}", "{fecha}, {cuando}");
    }

    @Test void elMensajeDeUnaVariableDesconocidaListaLasQueSiAdmite() {
        rechaza(AVISO, "Aviso", "Hola {nada}", "{razon_social}, {ruc}, {enlace}");
    }

    @Test void unaLlaveQueNoEsUnaVariableNoMolesta() {
        assertThat(AVISO.validar("Aviso", "Hola { no es variable } ni {Mayuscula} ni {con espacio} ni {}").cuerpo()).contains("{Mayuscula}");
    }

    @Test void laVariableIndispensableTieneQueEstarEnElCuerpo() {
        rechaza(ACCESO, "Restablecer", "Abre el portal y pide otro enlace", "{enlace}");
        rechaza(PlantillaDeCorreo.VERIFICACION_CORREO, "Verifica", "Entra", "{enlace}");
        rechaza(PlantillaDeCorreo.BIENVENIDA, "Hola", "Bienvenido {razon_social}", "{enlace}");
    }

    @Test void laVariableIndispensableNoCuentaSiSoloEstaEnElAsunto() {
        rechaza(ACCESO, "Tu enlace es {enlace}", "Mira tu correo", "{enlace}");
    }

    @Test void lasVariablesOpcionalesSePuedenOmitir() {
        assertThat(ACCESO.validar("Restablecer", "Abre {enlace}").cuerpo()).isEqualTo("Abre {enlace}");
    }

    @Test void unaVariableSePuedeUsarEnElAsuntoYEnElCuerpo() {
        Texto t = AVISO.validar("Aviso de {razon_social}", "Hola {razon_social} ({ruc})");

        assertThat(t.asunto()).isEqualTo("Aviso de {razon_social}");
    }

    @Test void cadaCorreoSoloAdmiteSusVariables() {
        rechaza(ACCESO, "Restablecer", "Abre {enlace} para {ruc}", "{ruc}");
        rechaza(PlantillaDeCorreo.AVISO_CREDENCIALES_SOL, "Venció {cuando}", "Hola", "{cuando}");
    }

    // --- renderizar ---------------------------------------------------------------------------------------------------------------------

    @Test void reemplazaCadaVariableEnElAsuntoYEnElCuerpo() {
        Texto t = AVISO.renderizar(new Texto("Aviso de {razon_social}", "{razon_social} (RUC {ruc}): {enlace}"),
                Map.of("razon_social", "SOL SAC", "ruc", "20100047226", "enlace", "https://app/x"));

        assertThat(t.asunto()).isEqualTo("Aviso de SOL SAC");
        assertThat(t.cuerpo()).isEqualTo("SOL SAC (RUC 20100047226): https://app/x");
    }

    @Test void unaVariableRepetidaSeReemplazaCadaVez() {
        assertThat(AVISO.renderizar(new Texto("a", "{ruc} y {ruc}"), Map.of("ruc", "1")).cuerpo()).isEqualTo("1 y 1");
    }

    @Test void unValorConLlavesNoSeVuelveAExpandir() {
        Texto t = AVISO.renderizar(new Texto("{razon_social}", "{razon_social} {ruc}"), Map.of("razon_social", "{ruc}", "ruc", "20100047226"));

        assertThat(t.asunto()).isEqualTo("{ruc}");
        assertThat(t.cuerpo()).isEqualTo("{ruc} 20100047226");
    }

    @Test void unValorConSimbolosDeReemplazoSeCopiaTalCual() {
        assertThat(AVISO.renderizar(new Texto("a", "{razon_social}"), Map.of("razon_social", "A$1\\B")).cuerpo()).isEqualTo("A$1\\B");
    }

    @Test void unaVariableSinValorQuedaALaVistaEnLugarDeBorrarseOFallar() {
        Texto t = AVISO.renderizar(new Texto("Aviso {razon_social}", "Hola {ruc} {enlace}"), Map.of("enlace", "https://app"));

        assertThat(t.asunto()).isEqualTo("Aviso {razon_social}");
        assertThat(t.cuerpo()).isEqualTo("Hola {ruc} https://app");
    }

    @Test void losSaltosDeLineaDeUnValorSeVuelvenEspaciosEnElAsuntoPeroNoEnElCuerpo() {
        Texto t = AVISO.renderizar(new Texto("Aviso {razon_social}", "Hola {razon_social}"), Map.of("razon_social", "SOL\r\nBcc: x@y.pe"));

        assertThat(t.asunto()).isEqualTo("Aviso SOL Bcc: x@y.pe");
        assertThat(t.cuerpo()).isEqualTo("Hola SOL\r\nBcc: x@y.pe");
    }

    @Test void unTextoSinVariablesSeDevuelveIgual() {
        assertThat(AVISO.renderizar(new Texto("Hola", "Chau"), Map.of("ruc", "1"))).isEqualTo(new Texto("Hola", "Chau"));
    }

    // --- vista previa -------------------------------------------------------------------------------------------------------------------

    @ParameterizedTest @EnumSource(PlantillaDeCorreo.class)
    void laVistaPreviaDelTextoDeFabricaNoDejaNingunaVariableSinReemplazar(PlantillaDeCorreo p) {
        Texto v = p.vistaPrevia(p.defecto());

        assertThat(v.asunto()).doesNotContain("{");
        assertThat(v.cuerpo()).doesNotContain("{");
    }

    @Test void laVistaPreviaUsaLosEjemplosDeCadaVariable() {
        Texto v = ACCESO.vistaPrevia(new Texto("Restablecer", "Abre {enlace} (válido {validez})"));

        assertThat(v.cuerpo()).isEqualTo("Abre https://app.khipu.pe/restablecer/0a1b2c3d (válido 1 hora)");
    }

    // --- el texto de fábrica es el que se mandaba antes ---------------------------------------------------------------------------------

    @Test void elTextoDeFabricaDeLosCorreosDeAccesoEsElDeSiempre() {
        Map<String, String> v = Map.of("enlace", "https://app/restablecer/T", "validez", "1 hora");

        assertThat(ACCESO.renderizar(ACCESO.defecto(), v)).isEqualTo(new Texto("Restablecer contraseña", "Para restablecer tu contraseña abre este enlace (válido 1 hora):\nhttps://app/restablecer/T"));
        assertThat(PlantillaDeCorreo.VERIFICACION_CORREO.renderizar(PlantillaDeCorreo.VERIFICACION_CORREO.defecto(), Map.of("enlace", "https://app/verificar/T", "validez", "24 horas")))
                .isEqualTo(new Texto("Verifica tu correo en khipu", "Para terminar de crear tu cuenta, verifica tu correo abriendo este enlace (válido 24 horas, de un solo uso):\nhttps://app/verificar/T"));
        assertThat(PlantillaDeCorreo.BIENVENIDA.renderizar(PlantillaDeCorreo.BIENVENIDA.defecto(), Map.of("enlace", "https://app/restablecer/T?invitacion=1", "razon_social", "SOL SAC", "ruc", "20100047226", "validez", "7 días")))
                .isEqualTo(new Texto("Te damos la bienvenida a khipu", "Dimos de alta a SOL SAC (RUC 20100047226) en khipu.\nPara entrar, crea tu contraseña en este enlace (válido 7 días, de un solo uso):\nhttps://app/restablecer/T?invitacion=1"));
    }
}
