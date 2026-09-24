import type { ChangeEvent, ComponentProps } from "react";
import type { UseFormRegisterReturn } from "react-hook-form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

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
  // El error se anuncia (`role="alert"`) y se ata al input (`aria-describedby`), como en `formularios/campo.tsx`: antes un
  // lector de pantalla solo oía «campo inválido», sin el motivo.
  const descripcion = error ? `${id}-error` : hint ? `${id}-ayuda` : undefined;
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type={type}
        autoComplete={autoComplete}
        inputMode={inputMode}
        placeholder={placeholder}
        maxLength={maxLength}
        aria-invalid={!!error}
        aria-describedby={descripcion}
        {...register}
        onChange={onChange}
      />
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-ayuda`} className="text-sm text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
