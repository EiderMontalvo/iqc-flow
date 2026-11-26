/** Un correo a medio escribir. `deIa` viaja con el borrador: si lo redactó la IA, el aviso de revisarlo no se pierde al cambiar de correo */
export interface Borrador { asunto: string; cuerpo: string; para?: string; cuenta?: string; marcarAtendido?: boolean; deIa?: boolean }

/**
 * Borradores por correo (y el del correo nuevo, con la clave «nuevo»). Viven en este módulo, no en una pantalla, así que sobreviven a ir
 * a otra pantalla y volver; y se copian a sessionStorage para que sobrevivan a recargar la página. Se pierden al cerrar la pestaña.
 */
const CLAVE = 'iqc-flow-borradores-correo';
const memoria = new Map<string, Borrador>();
let cargado = false;

function cargar() {
  if (cargado) return;
  cargado = true;
  try { const crudo = sessionStorage.getItem(CLAVE); if (crudo) for (const [k, v] of Object.entries(JSON.parse(crudo) as Record<string, Borrador>)) memoria.set(k, v); }
  catch { /* sin almacenamiento de sesión: solo en memoria */ }
}
function copiar() {
  try { sessionStorage.setItem(CLAVE, JSON.stringify(Object.fromEntries(memoria))); } catch { /* solo en memoria */ }
}

export const borradores = {
  leer(id: string) { cargar(); return memoria.get(id); },
  hay(id: string) { cargar(); return memoria.has(id); },
  guardar(id: string, b: Borrador) { cargar(); memoria.set(id, b); copiar(); },
  borrar(id: string) { cargar(); if (memoria.delete(id)) copiar(); },
};
