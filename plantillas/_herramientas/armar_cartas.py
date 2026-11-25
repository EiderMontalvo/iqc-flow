"""Arma las plantillas de cartas de IQC Flow (Word .docx con campos {{campo}}).

Uso: python armar_cartas.py <carpeta_salida> <logo.png> <firma_muestra.png>

Estructura común:
- membrete en el encabezado (todas las páginas): logo {{logo}} y datos de la marca, línea azul;
- cuerpo con campos;
- cierre (Atentamente, firma {{firma}}, nombre, cargo, empresa y logo) en un MARCO anclado al margen inferior
  (w:framePr vAnchor=margin yAlign=bottom wrap=notBeside): queda siempre al pie de la última página y el texto
  del cuerpo nunca pasa por su lado.
"""
import re
import sys
from copy import deepcopy

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

AZUL = RGBColor(0x1F, 0x3A, 0x93)
ANCHO_TEXTO_CM = 16.0  # A4 21 cm - 2.5 - 2.5


def base(logo):
    d = Document()
    s = d.sections[0]
    s.page_width, s.page_height = Cm(21.0), Cm(29.7)
    s.left_margin = s.right_margin = Cm(2.5)
    s.top_margin, s.bottom_margin = Cm(4.0), Cm(1.8)
    s.header_distance, s.footer_distance = Cm(0.8), Cm(0.8)

    normal = d.styles['Normal']
    normal.font.name = 'Times New Roman'
    normal.element.rPr.rFonts.set(qn('w:eastAsia'), 'Times New Roman')
    normal.font.size = Pt(11)
    pf = normal.paragraph_format
    pf.space_before, pf.space_after, pf.line_spacing = Pt(0), Pt(8), 1.1
    lb = d.styles['List Bullet']
    lb.font.name = 'Times New Roman'
    lb.paragraph_format.space_after = Pt(2)

    # membrete: logo | datos de la marca
    h = s.header
    h.is_linked_to_previous = False
    t = h.add_table(1, 2, Cm(ANCHO_TEXTO_CM))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    anchos = (Cm(4.6), Cm(ANCHO_TEXTO_CM - 4.6))
    for c, w in zip(t.rows[0].cells, anchos):
        c.width = w
    pl = t.cell(0, 0).paragraphs[0]
    imagen(pl.add_run(), logo, Cm(4.2), '{{logo}}')
    datos = t.cell(0, 1)
    lineas = [('{{marcaNombre}}', 17, True), ('{{marcaDireccion}}', 7.5, True), ('{{marcaTelefono}}', 10, True),
              ('Correo electrónico: {{marcaCorreo}}', 10, True)]
    for i, (texto, tam, negrita) in enumerate(lineas):
        p = datos.paragraphs[0] if i == 0 else datos.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.0
        r = p.add_run(texto)
        r.font.size, r.bold = Pt(tam), negrita
        if i != 1:
            r.font.color.rgb = AZUL
    linea = h.paragraphs[0]  # el párrafo vacío que trae el encabezado queda debajo de la tabla
    h._element.remove(linea._element)
    h._element.append(linea._element)
    borde(linea, 'bottom', '1F3A93', 12)
    linea.paragraph_format.space_after = Pt(0)
    return d


def imagen(run, ruta, ancho, alt):
    forma = run.add_picture(ruta, width=ancho)
    forma._inline.docPr.set('descr', alt)
    forma._inline.docPr.set('title', alt)
    return forma


def borde(p, lado, color, grosor):
    ppr = p._p.get_or_add_pPr()
    b = OxmlElement('w:pBdr')
    e = OxmlElement(f'w:{lado}')
    for k, v in (('w:val', 'single'), ('w:sz', str(grosor)), ('w:space', '1'), ('w:color', color)):
        e.set(qn(k), v)
    b.append(e)
    ppr.append(b)


MARCA = re.compile(r'(\*\*_.+?_\*\*|\*\*.+?\*\*|_.+?_)')


def parrafo(d, texto, alinear=WD_ALIGN_PARAGRAPH.JUSTIFY, estilo=None, despues=None):
    """texto con **negrita**, _cursiva_ y **_ambas_**; un campo {{x}} nunca se parte entre dos runs"""
    p = d.add_paragraph(style=estilo)
    p.alignment = alinear
    if despues is not None:
        p.paragraph_format.space_after = Pt(despues)
    for trozo in MARCA.split(texto):
        if not trozo:
            continue
        if trozo.startswith('**_'):
            r = p.add_run(trozo[3:-3]); r.bold = r.italic = True
        elif trozo.startswith('**'):
            r = p.add_run(trozo[2:-2]); r.bold = True
        elif trozo.startswith('_') and trozo.endswith('_') and len(trozo) > 2:
            r = p.add_run(trozo[1:-1]); r.italic = True
        else:
            p.add_run(trozo)
    return p


def marco(p):
    """todos los párrafos con el mismo framePr forman un solo marco, al pie de la página en que caen"""
    ppr = p._p.get_or_add_pPr()
    f = OxmlElement('w:framePr')
    for k, v in (('w:w', str(int(ANCHO_TEXTO_CM / 2.54 * 1440))), ('w:hSpace', '0'), ('w:vSpace', '180'),
                 ('w:wrap', 'notBeside'), ('w:vAnchor', 'margin'), ('w:hAnchor', 'margin'),
                 ('w:xAlign', 'left'), ('w:yAlign', 'bottom')):
        f.set(qn(k), v)
    estilo = ppr.find(qn('w:pStyle'))
    ppr.insert(1 if estilo is not None else 0, f)


def cierre(d, logo, firma, despedida='Atentamente,'):
    # Word: el último párrafo del cuerpo no pasa solo a otra página (LibreOffice no lo aplica antes de un marco)
    cuerpo = [p for p in d.paragraphs if p.text.strip()]
    if len(cuerpo) >= 2:
        cuerpo[-2].paragraph_format.keep_with_next = True
    filas = []
    p = d.add_paragraph(despedida); p.paragraph_format.space_after = Pt(2); filas.append(p)
    p = d.add_paragraph()  # firma a la izquierda, logo a la derecha
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(ANCHO_TEXTO_CM), WD_TAB_ALIGNMENT.RIGHT)
    imagen(p.add_run(), firma, Cm(5.2), '{{firma}}')
    p.add_run('\t')
    imagen(p.add_run(), logo, Cm(4.4), '{{logo}}')
    filas.append(p)
    for texto, negrita in (('{{firmanteNombre}}', True), ('{{firmanteCargo}}', False), ('{{marcaRazonSocial}}', False)):
        p = d.add_paragraph(); p.paragraph_format.space_after = Pt(0)
        r = p.add_run(texto); r.bold = negrita
        filas.append(p)
    for p in filas:
        p.paragraph_format.keep_with_next = True
        marco(p)
    filas[-1].paragraph_format.keep_with_next = False


def fecha(d):
    parrafo(d, '{{ciudad}}, {{fechaTexto}}', WD_ALIGN_PARAGRAPH.RIGHT, despues=14)


# ------------------------------------------------------------------ cartas

def carta_certificacion(logo, firma):
    d = base(logo)
    fecha(d)
    parrafo(d, 'Señores:', WD_ALIGN_PARAGRAPH.LEFT, despues=0)
    parrafo(d, '**{{razonSocial}}**', WD_ALIGN_PARAGRAPH.LEFT, despues=0)
    parrafo(d, 'Presente.–', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, '**Asunto:** Carta de aprobación del Sistema de Gestión en las normas {{normas}}', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, 'Tenemos el agrado de dirigirnos a ustedes a fin de enviarles por la presente carta la confirmación de '
               '{{tipoServicio}} de la empresa **_{{razonSocial}}_**.')
    parrafo(d, 'La mencionada empresa, identificada con **RUC {{numeroDocumento}}**, contrató el servicio de auditoría para '
               '{{tipoServicio}} con la empresa **{{marcaRazonSocial}}**, con **RUC {{marcaRuc}}** y dirección fiscal en '
               '{{marcaDireccionFiscal}}.')
    parrafo(d, 'La empresa **_{{razonSocial}}_** ha cumplido y cumple satisfactoriamente con la {{tipoServicio}} durante la '
               'evaluación de los Sistemas de Gestión {{normas}}, con el siguiente alcance:')
    parrafo(d, '**_{{alcance}}_**')
    parrafo(d, 'Actualmente los certificados se encuentran en proceso de emisión; mediante la presente se deja constancia.', despues=2)
    parrafo(d, 'Se expide el presente documento, de acuerdo a ley, para los fines que el interesado crea conveniente.')
    cierre(d, logo, firma)
    return d


def carta_suspension_pago(logo, firma):
    d = base(logo)
    fecha(d)
    parrafo(d, '**CARTA DE NOTIFICACIÓN DE SUSPENSIÓN DE CERTIFICACIONES**', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, 'Señores:', WD_ALIGN_PARAGRAPH.LEFT, despues=0)
    parrafo(d, '**{{razonSocial}}**', WD_ALIGN_PARAGRAPH.LEFT, despues=0)
    parrafo(d, 'Presente.–', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, '**Asunto:** Notificación de suspensión de las certificaciones {{normasSinEdicion}} por incumplimiento de '
               'obligaciones de pago.', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, 'De nuestra consideración:')
    parrafo(d, 'Por medio de la presente, {{marcaRazonSocial}}, en su calidad de organismo de certificación, se dirige a ustedes '
               'para comunicar formalmente la suspensión de las certificaciones otorgadas a {{razonSocial}}, correspondientes '
               'a las normas {{normas}}, debido al incumplimiento de las obligaciones de pago vinculadas a los servicios de '
               'certificación contratados.')
    parrafo(d, 'De acuerdo con nuestros registros administrativos, a la fecha se mantienen obligaciones económicas pendientes de '
               'regularización, situación que constituye un incumplimiento de las condiciones comerciales y contractuales '
               'aplicables a los servicios brindados por nuestra organización. Pese a la necesidad de mantener al día dichas '
               'obligaciones, no se ha concretado la regularización de los pagos correspondientes.')
    parrafo(d, 'En consecuencia, y de conformidad con los procedimientos de certificación y las condiciones contractuales '
               'aplicables, se comunica la suspensión de las certificaciones correspondientes a las siguientes normas:', despues=4)
    p = parrafo(d, '{{norma.codigo}}: {{norma.nombre}}.', WD_ALIGN_PARAGRAPH.LEFT, estilo='List Bullet')
    p.paragraph_format.space_after = Pt(8)
    parrafo(d, 'Durante el periodo de suspensión, la empresa deberá abstenerse de presentar sus certificaciones como vigentes, '
               'así como de realizar cualquier uso o declaración que implique que mantiene una condición de certificación '
               'activa, de acuerdo con las disposiciones aplicables al proceso de certificación.')
    parrafo(d, 'La suspensión se mantendrá hasta que se regularicen las obligaciones económicas pendientes y se cumplan las '
               'condiciones requeridas para evaluar el restablecimiento de las certificaciones. La eventual restitución de su '
               'estado activo estará sujeta a la verificación del cumplimiento de los requisitos correspondientes y a la '
               'decisión formal de {{marcaRazonSocial}}, conforme a sus procedimientos aplicables.')
    parrafo(d, 'Por lo expuesto, solicitamos a su representada adoptar las medidas necesarias para regularizar los pagos '
               'pendientes y comunicarse con nuestra área administrativa a fin de coordinar las acciones correspondientes. '
               'La falta de regularización podrá dar lugar a la evaluación de medidas adicionales, incluida la cancelación de '
               'las certificaciones, cuando corresponda conforme a los procedimientos y condiciones aplicables.')
    parrafo(d, 'La presente comunicación tiene por finalidad dejar constancia formal de la medida adoptada y de la necesidad de '
               'regularizar las obligaciones pendientes para evaluar la continuidad de la relación de certificación.')
    parrafo(d, 'Sin otro particular, esperamos su pronta atención y regularización.')
    cierre(d, logo, firma)
    return d


def carta_continuidad(logo, firma):
    d = base(logo)
    fecha(d)
    parrafo(d, '**_{{razonSocial}}_**', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, '**Asunto: Carta de Continuidad de las Certificaciones {{normas}} – {{etapa}}.**', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, '**Estimado señor / señora:**', WD_ALIGN_PARAGRAPH.LEFT, despues=10)
    parrafo(d, 'Por medio de la presente, y como resultado del proceso de **auditoría de {{etapa}}** realizado a su {{sistema}}, '
               'hemos revisado el Informe de Evaluación presentado por nuestro equipo auditor, el cual se fundamenta en las '
               'evidencias objetivas obtenidas durante la auditoría, las muestras evaluadas y el cumplimiento del plan de '
               'auditoría previamente establecido.')
    parrafo(d, 'Como resultado de la **auditoría de {{etapa}}**, se ratifica la **continuidad de las certificaciones** de los '
               'sistemas de gestión conforme a las normas **{{normas}}**, al haberse evidenciado el cumplimiento satisfactorio '
               'de los objetivos de la auditoría y la conformidad del {{sistema}} con los requisitos establecidos en las normas '
               'aplicables. Asimismo, se evidenció el mantenimiento de un adecuado control sobre sus procesos y operaciones.')
    parrafo(d, 'De igual manera, se deja constancia de que el **servicio correspondiente a la {{etapa}}** fue ejecutado de manera '
               'satisfactoria y forma parte del proceso de mantenimiento de las certificaciones antes mencionadas.')
    parrafo(d, 'Felicitamos a todo su equipo por el compromiso demostrado en el mantenimiento y mejora continua de su {{sistema}}, '
               'reflejado en la eficacia de su implementación, el cumplimiento de los requisitos normativos y de cualquier '
               'requisito contractual adicional aplicable al proceso de **{{etapa}}**.')
    parrafo(d, 'Asimismo, confirmamos nuestro compromiso con la confidencialidad de toda la información obtenida durante la '
               'prestación de los servicios de certificación. Dicha información no será divulgada a ninguna persona o entidad '
               'externa, salvo cuando exista una obligación legal aplicable o previa autorización expresa de su representada.')
    parrafo(d, 'Finalmente, agradecemos la confianza depositada en nuestra organización para la evaluación y mantenimiento de sus '
               'certificaciones. Reiteramos nuestro compromiso de continuar brindándoles un servicio de certificación con los más '
               'altos estándares de calidad, imparcialidad y profesionalismo, acompañándolos en el fortalecimiento y mejora '
               'continua de su {{sistema}}.')
    cierre(d, logo, firma)
    return d


def carta_base(logo, firma):
    """punto de partida para cartas nuevas: se cambia solo el cuerpo, el membrete y el cierre ya están"""
    d = base(logo)
    fecha(d)
    parrafo(d, 'Señores:', WD_ALIGN_PARAGRAPH.LEFT, despues=0)
    parrafo(d, '**{{razonSocial}}**', WD_ALIGN_PARAGRAPH.LEFT, despues=0)
    parrafo(d, 'Presente.–', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, '**Asunto:** {{asunto}}', WD_ALIGN_PARAGRAPH.LEFT, despues=12)
    parrafo(d, 'De nuestra consideración:')
    parrafo(d, 'Escriba aquí el cuerpo de la carta. Puede usar cualquier campo de la lista, por ejemplo {{razonSocial}}, '
               '{{normas}} o {{marcaRazonSocial}}. Agregue o quite párrafos libremente: el bloque de firma de abajo es un marco '
               'anclado al pie de la página y se mantiene siempre al final de la última hoja.')
    parrafo(d, 'Sin otro particular, quedamos a su disposición.')
    cierre(d, logo, firma)
    return d


if __name__ == '__main__':
    salida, logo, firma = sys.argv[1:4]
    for nombre, f in (('carta-certificacion.docx', carta_certificacion), ('carta-suspension-pago.docx', carta_suspension_pago),
                      ('carta-continuidad.docx', carta_continuidad), ('carta-base.docx', carta_base)):
        f(logo, firma).save(f'{salida}/{nombre}')
        print('ok', nombre)
