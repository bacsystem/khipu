import type { ChangeEvent, ComponentProps } from "react";
import type { UseFormRegisterReturn } from "react-hook-form";
import { Campo, Entrada } from "khipu-design-system/formularios/campo";

/**
 * Campo de un formulario hecho con react-hook-form: el `Campo` y la `Entrada` del panel (etiqueta de 12 px, control `CAMPO` de h-control, error en rojo o ayuda
 * en mono), con el registro del formulario ya conectado. Los formularios de acceso (login, registro, recuperar, restablecer) lo usan para verse igual que el panel.
 */
export function FormField({
  id,
  label,
  type = "text",
  register,
  error,
  autoComplete,
  inputMode,
  placeholder,
  hint,
  maxLength,
  filtrar,
}: {
  id: string;
  label: string;
  type?: string;
  register: UseFormRegisterReturn;
  error?: string;
  autoComplete?: string;
  inputMode?: ComponentProps<"input">["inputMode"];
  placeholder?: string;
  hint?: string;
  maxLength?: number;
  /**
   * Corrige lo tecleado antes de que llegue al formulario: un campo de celular o de RUC no tiene por qué aceptar letras.
   * `maxLength` limita el largo pero no el tipo de carácter, y sin esto se escribía «907892868sssss» y el usuario solo se
   * enteraba al enviar.
   */
  filtrar?: (valor: string) => string;
}) {
  const onChange = filtrar
    ? (e: ChangeEvent<HTMLInputElement>) => {
        e.target.value = filtrar(e.target.value);
        return register.onChange(e);
      }
    : register.onChange;
  // El error se anuncia (`role="alert"`, lo pone `Campo`) y se ata al input (`aria-describedby`): antes un lector de pantalla solo oía «campo inválido», sin el motivo.
  const descripcion = error ? `${id}-error` : hint ? `${id}-ayuda` : undefined;
  return (
    <Campo id={id} etiqueta={label} error={error} ayuda={hint}>
      <Entrada
        id={id}
        type={type}
        autoComplete={autoComplete}
        inputMode={inputMode}
        placeholder={placeholder}
        maxLength={maxLength}
        invalido={!!error}
        aria-describedby={descripcion}
        {...register}
        onChange={onChange}
      />
    </Campo>
  );
}
