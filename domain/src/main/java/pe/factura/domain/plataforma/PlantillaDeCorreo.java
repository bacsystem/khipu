package pe.factura.domain.plataforma;

import pe.factura.domain.DomainException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los correos que la plataforma le manda a una persona, con el texto que traen de fábrica y las variables que cada uno admite (#199). Un administrador puede reemplazar el asunto
 * y el cuerpo; lo que no cambió sigue siendo el texto de fábrica, que es también al que se vuelve al restaurar.
 *
 * <p>Una variable se escribe {@code {nombre}}. Al guardar se rechaza lo que no se podría mandar bien: una variable que el correo no tiene (se mandaría con la llave a la vista), la que
 * falta en un correo donde es indispensable (el {@code {enlace}} de los de acceso: sin él nadie puede entrar), un asunto con saltos de línea (cabecera de correo) o un texto
 * desmedido. Al mandar, cada variable se reemplaza **una sola vez**: un valor que contuviera {@code {otra}} no se vuelve a expandir.
 */
public enum PlantillaDeCorreo {
    VERIFICACION_CORREO("Verificación de correo", "Se manda al registrarse, y cuando alguien pide o un administrador reenvía el enlace para verificar el correo.",
            "Verifica tu correo en khipu",
            "Para terminar de crear tu cuenta, verifica tu correo abriendo este enlace (válido {validez}, de un solo uso):\n{enlace}",
            List.of(Variable.indispensable("enlace", "El enlace de un solo uso para verificar el correo.", "https://app.khipu.pe/verificar/0a1b2c3d"),
                    Variable.opcional("validez", "Cuánto dura el enlace.", "24 horas"))),

    RECUPERACION_CLAVE("Restablecer la contraseña", "Se manda cuando alguien olvidó su contraseña, o un administrador le envía el enlace para restablecerla.",
            "Restablecer contraseña",
            "Para restablecer tu contraseña abre este enlace (válido {validez}):\n{enlace}",
            List.of(Variable.indispensable("enlace", "El enlace de un solo uso para elegir una contraseña nueva.", "https://app.khipu.pe/restablecer/0a1b2c3d"),
                    Variable.opcional("validez", "Cuánto dura el enlace.", "1 hora"))),

    BIENVENIDA("Bienvenida de un cliente dado de alta", "Se manda a quien un administrador dio de alta, para que cree su contraseña.",
            "Te damos la bienvenida a khipu",
            "Dimos de alta a {razon_social} (RUC {ruc}) en khipu.\nPara entrar, crea tu contraseña en este enlace (válido {validez}, de un solo uso):\n{enlace}",
            List.of(Variable.indispensable("enlace", "El enlace de un solo uso para crear la contraseña.", "https://app.khipu.pe/restablecer/0a1b2c3d?invitacion=1"),
                    Variable.opcional("razon_social", "La razón social de la empresa dada de alta.", "PANADERIA SOL SAC"),
                    Variable.opcional("ruc", "El RUC de la empresa.", "20100047226"),
                    Variable.opcional("validez", "Cuánto dura el enlace.", "7 días"))),

    AVISO_CERTIFICADO_POR_VENCER("Aviso: certificado por vencer", "Se manda desde «Avisos» cuando el certificado digital de una empresa vence en menos de 30 días.",
            "Tu certificado digital de {razon_social} vence {cuando}",
            "Hola,\n\nEl certificado digital de {razon_social} (RUC {ruc}) vence el {fecha} ({cuando}). Cuando venza, khipu ya no podrá firmar los comprobantes de esta empresa y no podrás emitir."
                    + "\n\nPara renovarlo, consigue un certificado nuevo y cárgalo en el portal:\n{enlace}\n\nSi ya lo renovaste, ignora este mensaje.\n\nkhipu",
            AvisoDeCertificado.VARIABLES),

    AVISO_CERTIFICADO_VENCIDO("Aviso: certificado vencido", "Se manda desde «Avisos» cuando el certificado digital de una empresa ya venció.",
            "El certificado digital de {razon_social} venció",
            "Hola,\n\nEl certificado digital de {razon_social} (RUC {ruc}) venció el {fecha}. Mientras no cargues uno vigente, khipu no puede firmar los comprobantes de esta empresa y no podrás emitir."
                    + "\n\nConsigue un certificado nuevo y cárgalo en el portal:\n{enlace}\n\nkhipu",
            AvisoDeCertificado.VARIABLES),

    AVISO_CREDENCIALES_SOL("Aviso: credenciales SOL", "Se manda desde «Avisos» cuando SUNAT no acepta el usuario o la clave SOL de una empresa.",
            "SUNAT no acepta las credenciales SOL de {razon_social}",
            "Hola,\n\nLos envíos de {razon_social} (RUC {ruc}) a SUNAT están fallando porque SUNAT no acepta el usuario o la clave SOL cargados. Los comprobantes quedan pendientes hasta que se corrijan."
                    + "\n\nRevísalos y vuelve a guardarlos en el portal:\n{enlace}\n\nSi ya las corregiste, ignora este mensaje.\n\nkhipu",
            List.of(Variable.opcional("razon_social", "La razón social de la empresa.", "PANADERIA SOL SAC"),
                    Variable.opcional("ruc", "El RUC de la empresa.", "20100047226"),
                    Variable.opcional("enlace", "El portal, donde el cliente corrige sus credenciales.", "https://app.khipu.pe")));

    public static final int MAX_ASUNTO = 150;
    public static final int MAX_CUERPO = 5000;

    private static final Pattern VARIABLE = Pattern.compile("\\{([a-z_]+)}");

    /** Una variable que el correo admite: lo que es, un valor de ejemplo para la vista previa y si el correo no sirve sin ella. */
    public record Variable(String nombre, String descripcion, String ejemplo, boolean indispensable) {
        static Variable indispensable(String nombre, String descripcion, String ejemplo) { return new Variable(nombre, descripcion, ejemplo, true); }
        static Variable opcional(String nombre, String descripcion, String ejemplo) { return new Variable(nombre, descripcion, ejemplo, false); }
        public String marca() { return "{" + nombre + "}"; }
    }

    /** Asunto y cuerpo de un correo, ya sea el de fábrica, uno guardado o uno ya con sus variables reemplazadas. */
    public record Texto(String asunto, String cuerpo) {}

    private static final class AvisoDeCertificado {
        static final List<Variable> VARIABLES = List.of(
                Variable.opcional("razon_social", "La razón social de la empresa.", "PANADERIA SOL SAC"),
                Variable.opcional("ruc", "El RUC de la empresa.", "20100047226"),
                Variable.opcional("fecha", "El día en que vence o venció el certificado (dd/mm/aaaa).", "25/10/2026"),
                Variable.opcional("cuando", "Cuándo vence o venció: «hoy», «mañana», «ayer», «en N días» o «hace N días».", "en 10 días"),
                Variable.opcional("enlace", "El portal, donde el cliente carga su certificado.", "https://app.khipu.pe"));
    }

    private final String etiqueta;
    private final String cuandoSeManda;
    private final Texto defecto;
    private final List<Variable> variables;

    PlantillaDeCorreo(String etiqueta, String cuandoSeManda, String asunto, String cuerpo, List<Variable> variables) {
        this.etiqueta = etiqueta;
        this.cuandoSeManda = cuandoSeManda;
        this.defecto = new Texto(asunto, cuerpo);
        this.variables = variables;
    }

    public String etiqueta() { return etiqueta; }

    public String cuandoSeManda() { return cuandoSeManda; }

    /** El texto de fábrica. */
    public Texto defecto() { return defecto; }

    public List<Variable> variables() { return variables; }

    public static Optional<PlantillaDeCorreo> deNombre(String nombre) {
        for (PlantillaDeCorreo p : values()) if (p.name().equals(nombre)) return Optional.of(p);
        return Optional.empty();
    }

    /**
     * Comprueba un texto antes de guardarlo y lo devuelve normalizado (asunto sin espacios en los bordes; cuerpo con saltos de línea {@code \n} y sin espacios al final). Falla con
     * {@code PLANTILLA_INVALIDA} y un mensaje que dice qué corregir.
     */
    public Texto validar(String asunto, String cuerpo) {
        String a = asunto == null ? "" : asunto.strip();
        String c = cuerpo == null ? "" : cuerpo.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
        if (a.isEmpty()) throw invalida("El asunto no puede estar vacío");
        if (a.length() > MAX_ASUNTO) throw invalida("El asunto admite hasta " + MAX_ASUNTO + " caracteres");
        if (a.chars().anyMatch(Character::isISOControl)) throw invalida("El asunto va en una sola línea");
        // `c` ya no tiene espacios al final: un cuerpo en blanco quedó vacío.
        if (c.isEmpty()) throw invalida("El cuerpo no puede estar vacío");
        if (c.length() > MAX_CUERPO) throw invalida("El cuerpo admite hasta " + MAX_CUERPO + " caracteres");
        if (c.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\t')) throw invalida("El cuerpo tiene caracteres de control que no se pueden mandar");

        Set<String> usadas = new LinkedHashSet<>();
        for (String texto : List.of(a, c)) {
            Matcher m = VARIABLE.matcher(texto);
            while (m.find()) usadas.add(m.group(1));
        }
        List<String> desconocidas = new ArrayList<>();
        for (String u : usadas) if (variables.stream().noneMatch(v -> v.nombre().equals(u))) desconocidas.add("{" + u + "}");
        if (!desconocidas.isEmpty())
            throw invalida("Este correo no tiene la variable " + String.join(", ", desconocidas) + ". Las que admite son: " + String.join(", ", variables.stream().map(Variable::marca).toList()));
        for (Variable v : variables)
            if (v.indispensable() && !c.contains(v.marca())) throw invalida("El cuerpo tiene que incluir " + v.marca() + ": sin eso el correo no sirve (" + v.descripcion() + ")");
        return new Texto(a, c);
    }

    /**
     * Reemplaza las variables por sus valores, cada una una sola vez. Una variable sin valor queda tal cual (a la vista) en vez de borrarse o fallar: un correo de acceso no puede
     * dejar de salir por un texto mal editado. El asunto va en una línea: los saltos de línea de un valor se vuelven espacios.
     */
    public Texto renderizar(Texto plantilla, Map<String, String> valores) {
        return new Texto(reemplazar(plantilla.asunto(), valores, true), reemplazar(plantilla.cuerpo(), valores, false));
    }

    /** Cómo se vería el texto con valores de ejemplo, para que el administrador lo revise antes de guardarlo. */
    public Texto vistaPrevia(Texto plantilla) {
        Map<String, String> ejemplos = new java.util.HashMap<>();
        for (Variable v : variables) ejemplos.put(v.nombre(), v.ejemplo());
        return renderizar(plantilla, ejemplos);
    }

    private static String reemplazar(String texto, Map<String, String> valores, boolean unaLinea) {
        Matcher m = VARIABLE.matcher(texto);
        StringBuilder r = new StringBuilder();
        while (m.find()) {
            String valor = valores.get(m.group(1));
            if (valor == null) valor = m.group();
            else if (unaLinea) valor = valor.replaceAll("[\\r\\n]+", " ");
            m.appendReplacement(r, Matcher.quoteReplacement(valor));
        }
        m.appendTail(r);
        return r.toString();
    }

    private static DomainException invalida(String mensaje) { return new DomainException("PLANTILLA_INVALIDA", mensaje); }
}
