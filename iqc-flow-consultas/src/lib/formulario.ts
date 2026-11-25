import type { FormEvent, InvalidEvent } from 'react';

/** Mensajes de validación del navegador en español, aunque el navegador esté en otro idioma */
export function validarEnEspanol(e: InvalidEvent<HTMLInputElement>) {
  const i = e.currentTarget;
  i.setCustomValidity(i.validity.valueMissing ? 'Completa este dato.' : i.validity.tooShort ? `Escribe al menos ${i.minLength} caracteres.` : '');
}

/** Al volver a escribir se borra el mensaje anterior para que el navegador valide de nuevo */
export const limpiarValidacion = (e: FormEvent<HTMLInputElement>) => e.currentTarget.setCustomValidity('');
