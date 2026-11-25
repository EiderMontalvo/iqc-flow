/**
 * Hipotrocoide: la roseta de los billetes y certificados de seguridad. Devuelve el trazo como `d` de un `<path>`.
 * @param cx, cy centro; escala multiplica el radio; R, r, dd son los parámetros de la curva
 */
export function roseta(cx: number, cy: number, escala: number, R = 7, r = 3, dd = 5.2, pasos = 900): string {
  let d = '';
  for (let i = 0; i <= pasos; i++) {
    const t = (i / pasos) * Math.PI * 2 * r;
    const x = (R - r) * Math.cos(t) + dd * Math.cos(((R - r) / r) * t);
    const y = (R - r) * Math.sin(t) - dd * Math.sin(((R - r) / r) * t);
    d += `${i === 0 ? 'M' : 'L'}${(cx + x * escala).toFixed(1)},${(cy + y * escala).toFixed(1)}`;
  }
  return d + 'Z';
}
