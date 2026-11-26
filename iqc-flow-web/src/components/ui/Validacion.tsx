/**
 * Validación propia de los formularios del panel (con `noValidate` en el form). El navegador acepta un campo `required`
 * con solo espacios; aquí no. Los errores se calculan en cada render una vez que se intentó enviar, así se corrigen
 * en vivo; el campo con error lleva `aria-invalid` y su mensaje debajo, y se enfoca el primero.
 */

/** Asterisco rojo junto a la etiqueta de un campo obligatorio (el lector de pantalla ya lee `required`) */
export function Obligatorio() {
  return <span className="obligatorio" aria-hidden>*</span>;
}

/** Mensaje bajo el campo; nada si no hay error */
export function ErrorCampo({ mensaje }: { mensaje?: string }) {
  return mensaje ? <span className="field-error">{mensaje}</span> : null;
}

export const vacio = (v: string | null | undefined) => !v || !v.trim();
export const correoValido = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim());

/** Después de marcar los errores, lleva el foco (y la vista) al primer campo inválido del formulario */
export function enfocarPrimerError(formulario: HTMLFormElement | null) {
  window.requestAnimationFrame(() => {
    const campo = formulario?.querySelector<HTMLElement>('[aria-invalid="true"]');
    campo?.focus(); campo?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  });
}
