import Link from "next/link";
import { Codigo, NotaProsa, P, PaginaGuia, Seccion, Tabla } from "@/components/developers/guia/prosa";
import { CODIGOS, ESTADOS, HTTP } from "@/components/developers/errores-datos";
import { messages } from "@/lib/messages";

export const metadata = { title: `Errores y estados · ${messages.app.nombre}` };

const INDICE = [
  { id: "estados", label: "Estados del comprobante" },
  { id: "http", label: "Códigos HTTP" },
  { id: "codigos", label: "Códigos de error de khipu" },
  { id: "sunat", label: "Códigos de SUNAT" },
];

export default function ErroresPage() {
  return (
    <PaginaGuia
      titulo="Errores y estados"
      resumen="Qué significa cada estado de un comprobante, cada código HTTP y cada código de error de la API, y qué hacer en cada caso. Cuando la regla viene de SUNAT, el mensaje lleva su código oficial."
      indice={INDICE}
    >
      <Seccion id="estados" titulo="Estados del comprobante">
        <P>
          El ciclo normal es <Codigo>RECIBIDO</Codigo> → <Codigo>FIRMADO</Codigo> → <Codigo>ENVIADO</Codigo> → <Codigo>ACEPTADO</Codigo>. Los demás
          estados son desvíos con una acción concreta:
        </P>
        <Tabla cabeceras={["Estado", "Significado", "Qué hacer"]} filas={ESTADOS.map(([e, s, q]) => [e, s, q])} />
        <NotaProsa tono="aviso">
          <Codigo>ERROR_ENVIO</Codigo> no significa que SUNAT rechazó nada: el comprobante ya tiene número y firma, y khipu lo entregará. Solo{" "}
          <Codigo>RECHAZADO</Codigo> exige corregir y volver a emitir.
        </NotaProsa>
      </Seccion>

      <Seccion id="http" titulo="Códigos HTTP">
        <Tabla cabeceras={["HTTP", "Cuándo"]} filas={HTTP.map(([h, c]) => [h, c])} />
      </Seccion>

      <Seccion id="codigos" titulo="Códigos de error de khipu">
        <P>
          Van en <Codigo>codigo</Codigo> del sobre de respuesta y son estables: programe contra ellos, no contra el texto de <Codigo>mensaje</Codigo>.
        </P>
        <Tabla cabeceras={["Código", "HTTP", "Significado", "Qué hacer"]} filas={CODIGOS.map(([c, h, s, q]) => [c, h, s, q])} />
      </Seccion>

      <Seccion id="sunat" titulo="Códigos de SUNAT">
        <P>
          SUNAT responde con un código numérico que khipu conserva en <Codigo>cdr.codigo</Codigo> (respuesta de SUNAT) o al inicio de{" "}
          <Codigo>mensaje</Codigo> (cuando khipu aplica la misma regla antes de enviar). Los rangos:
        </P>
        <Tabla
          cabeceras={["Rango", "Tipo", "Qué hacer"]}
          filas={[
            ["0", "Aceptado.", "Nada: comprobante válido."],
            ["0100 – 0999", "Fallo del servicio de SUNAT o de autenticación (0100 sistema no disponible, 0102 usuario o clave SOL incorrectos, 0111 sin perfil para emitir).", "khipu reintenta solo (ERROR_ENVIO). Si es 010x de credenciales, corrija usuario/clave SOL en el portal."],
            ["1000 – 1999", "Error del emisor en el envío: nombre de archivo, XML vacío, comprobante ya registrado con otros datos (1033), emisor no autorizado (1078).", "El comprobante queda RECHAZADO sin reintentos; revise el mensaje y emita de nuevo."],
            ["2000 – 3999", "Rechazo por validación del contenido (importes, catálogos, forma de pago, receptor…).", "RECHAZADO: el número queda utilizado. Corrija el dato indicado y emita un comprobante nuevo, con otro número."],
            ["4000 – 4999", "Observación: aceptado, pero un dato conviene corregirlo. Por ejemplo la 4443, que SUNAT puede dar a una boleta informada en el resumen diario pasado el 7.º día (en e-beta no la dio).", "ACEPTADO_CON_OBS: válido; corrija en próximas emisiones (en la 4443, envíe las boletas a tiempo)."],
          ]}
        />
        <P>
          Ejemplos de reglas que khipu comprueba localmente con el mismo código: <Codigo>3244</Codigo> falta la forma de pago, <Codigo>3249</Codigo>{" "}
          crédito sin cuotas, <Codigo>3319</Codigo> las cuotas no suman el pendiente, <Codigo>3267</Codigo> cuota que vence antes de la emisión. La tabla
          completa de códigos de retorno la publica SUNAT en sus reglas de validación; los catálogos de códigos están en{" "}
          <Link href="/developers/catalogos" className="text-primary hover:underline">
            Catálogos SUNAT
          </Link>
          .
        </P>
      </Seccion>
    </PaginaGuia>
  );
}
