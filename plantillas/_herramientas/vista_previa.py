"""Vista previa de una plantilla Word: la llena con datos de ejemplo y la convierte a PDF con LibreOffice.

Simula lo que hará IQC Flow al generar el documento, para revisar una plantilla nueva antes de usarla.

Uso (en el servidor, desde la carpeta plantillas/):
    python3 _herramientas/vista_previa.py cartas/carta-continuidad.docx _herramientas/datos-ejemplo.json /tmp/vista

Requiere: pip install python-docx  ·  LibreOffice (soffice) instalado.

Reglas que aplica (las mismas del README):
- {{campo}} se reemplaza por su valor; un campo sin dato queda vacío;
- un párrafo con campos {{norma.x}} se repite por cada elemento de "normasLista";
- una imagen cuyo texto alternativo es {{firma}} o {{logo}} se cambia por la ruta indicada en "_imagenes"
  (si no se indica, queda la imagen de muestra).
"""
import json
import os
import re
import subprocess
import sys
from copy import deepcopy

from docx import Document
from docx.oxml.ns import qn
from docx.text.paragraph import Paragraph

CAMPO = re.compile(r'\{\{([\w.]+)\}\}')


def reemplazar(p, datos):
    for r in p.runs:
        if '{{' in r.text:
            r.text = CAMPO.sub(lambda m: str(datos.get(m.group(1), '')), r.text)


def parrafos(d):
    yield from d.paragraphs
    for t in d.tables:
        for c in t._cells:
            yield from c.paragraphs
    for s in d.sections:
        for parte in (s.header, s.footer):
            yield from parte.paragraphs
            for t in parte.tables:
                for c in t._cells:
                    yield from c.paragraphs


def llenar(plantilla, datos):
    d = Document(plantilla)
    for p in list(d.paragraphs):
        if '{{norma.' in p.text:
            for n in datos.get('normasLista', []):
                copia = deepcopy(p._p)
                p._p.addprevious(copia)
                reemplazar(Paragraph(copia, p._parent), {f'norma.{k}': v for k, v in n.items()})
            p._p.getparent().remove(p._p)
    for p in parrafos(d):
        reemplazar(p, datos)
    imagenes = datos.get('_imagenes', {})
    for parte in [d.part] + [s.header.part for s in d.sections]:
        for docpr in parte.element.iter(qn('wp:docPr')):
            ruta = imagenes.get((docpr.get('descr') or '').strip('{}'))
            if ruta:
                rid = next(docpr.getparent().iter(qn('a:blip'))).get(qn('r:embed'))
                parte.related_parts[rid]._blob = open(ruta, 'rb').read()
    sobrantes = sorted({m for p in parrafos(d) for m in CAMPO.findall(p.text)})
    return d, sobrantes


def main(plantilla, archivo_datos, carpeta):
    os.makedirs(carpeta, exist_ok=True)
    datos = json.load(open(archivo_datos, encoding='utf-8'))
    d, sobrantes = llenar(plantilla, datos)
    nombre = os.path.splitext(os.path.basename(plantilla))[0]
    docx = os.path.join(carpeta, f'{nombre}-vista.docx')
    d.save(docx)
    subprocess.run(['soffice', '--headless', '--convert-to', 'pdf', '--outdir', carpeta, docx],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
    print('PDF:', os.path.join(carpeta, f'{nombre}-vista.pdf'))
    if sobrantes:
        print('Campos de la plantilla que no están en los datos de ejemplo (salen vacíos):', ', '.join(sobrantes))


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:4])
