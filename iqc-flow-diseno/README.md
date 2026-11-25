# Sistema de diseño IQC (iqc-flow-diseno)

Fuentes, colores, tipografía, espacios, formas, movimiento y el símbolo de IQC, compartidos por las dos interfaces de IQC Flow:

| App | Qué usa del sistema |
|---|---|
| `iqc-flow-consultas` (portal público) | Todo; solo modo claro. Portada en negro de marca, resultados en papel |
| `iqc-flow-web` (panel interno) | Todo; claro y oscuro. Carril y acceso en negro de marca, área de trabajo en papel |

Si un color, un tamaño o una sombra hace falta en las dos apps, se agrega aquí, no en cada app.

## Contenido

```
iqc-flow-diseno/
├── css/
│   ├── fuentes.css     Public Sans (variable 100–900) y Source Serif 4 600, servidas desde el propio sitio
│   ├── tokens.css      Variables CSS: primitivos de marca, semánticos (claro y oscuro) y escalas
│   └── navegador.css   Selección de texto, cursor, foco, barras de desplazamiento y cifras tabulares
├── fuentes/            Archivos woff2 (latín y latín extendido)
└── simbolo.ts          Trazado SVG del símbolo IQC y paradas del degradado del logo
```

## Cómo se conecta una app

**1. Vite** (`vite.config.ts`):

```ts
resolve: { alias: { '@iqc/diseno': fileURLToPath(new URL('../iqc-flow-diseno', import.meta.url)) } },
server: { fs: { allow: ['..'] } },
```

**2. TypeScript** (`tsconfig.app.json`): `"paths": { "@iqc/diseno/*": ["../iqc-flow-diseno/*"] }` y `"../iqc-flow-diseno/*.ts"` en `include`.

**3. CSS de entrada de la app:**

```css
@import "@iqc/diseno/css/fuentes.css";
@import "@iqc/diseno/css/tokens.css";
@import "@iqc/diseno/css/navegador.css";
```

Al compilar, Vite copia las fuentes y el código del símbolo dentro de cada app. El sitio publicado no depende de esta carpeta.

## Tokens

### 1. Primitivos de marca (`--iqc-*`)

Son los colores de IQC tal cual y no cambian con el tema. Se usan solo para lo que es marca en sí.

| Token | Valor | Uso |
|---|---|---|
| `--iqc-negro` | #000000 | Negro de iqcperu.com: portada del portal, carril y acceso del panel |
| `--iqc-tinta`, `--iqc-navy`, `--iqc-blanco` | #1a1a2e, #00192d, #ffffff | Colores obligatorios de la marca |
| `--iqc-azul` / `--iqc-azul-vivo` | #0019d2 / #1a33e0 | Azul IQC. El vivo es el de los botones sobre negro |
| `--iqc-degradado` | #d90435 → #9002ae → #6701e0 → #0019d2 | Degradado del logo. **Solo en líneas**: filetes y el contorno del símbolo |

### 2. Semánticos

Cambian con el tema: claro por defecto y oscuro con `<html data-theme="dark">`.

| Grupo | Tokens |
|---|---|
| Superficies | `--fondo` (papel), `--superficie`, `--superficie-2`, `--superficie-hundida` |
| Bordes | `--borde`, `--borde-suave`, `--borde-fuerte` |
| Texto | `--texto`, `--texto-suave`, `--texto-2` (secundario), `--texto-3` (etiquetas y ayudas) |
| Acción | `--accion`, `--accion-hover`, `--accion-texto`, `--accion-suave`, `--foco`, `--foco-anillo`, `--enlace` |
| Estados (tintas del sello) | `--ok` (vigente), `--alerta` (por vencer), `--mal` (vencido o error), `--pausa` (suspendido), `--neutro` (sin fecha o archivado). Cada uno tiene su versión `-suave` para fondos |
| Cromo (zonas negras de marca) | `--cromo`, `--cromo-texto`, `--cromo-texto-2`, `--cromo-hover`, `--cromo-activo`, `--cromo-borde`, `--foco-cromo` |
| Gráficos | `--grafico`, `--grafico-hover`, `--rejilla`; categórica `--serie-1` a `--serie-3` (partes de un total) |
| Profundidad | `--sombra-hoja`, `--sombra-flotante`, `--sombra-lateral`, `--velo` |

Todos los textos cumplen un contraste de 4.5:1 o más en ambos temas; está medido. Los colores de gráficos se validaron con la skill de visualización: `#1a33e0` sobre blanco y `#6b7bff` sobre `#14151c`.

### 3. Escalas

- **Tipografía:**
  - `--fuente` (Public Sans) para todo;
  - `--fuente-documento` (Source Serif 4) solo para el nombre del titular de un certificado;
  - tamaños fijos de `--tam-xs` (12 px) a `--tam-3xl` (28 px).
- **Espacios:** rejilla de 4 px, de `--e1` (4) a `--e12` (48).
- **Radios:** `--radio-sm` (6), `--radio` (8) y `--radio-lg` (10).
- **Movimiento:** curva `--salida` y duraciones `--rapido` (150 ms) y `--normal` (220 ms).

## Reglas del sistema

1. **El degradado del logo es línea, nunca relleno:** filetes de 2 px y el contorno del símbolo.
2. **Sobre negro no hay tarjetas ni bloques claros.** Los avisos sobre negro van en el toast oscuro.
3. **El estado de un certificado** se comunica con su tinta y con una palabra, nunca solo con el color.
4. **Gráficos:**
   - una serie por gráfico, en `--grafico`; las partes de un total usan `--serie-1…3` en ese orden fijo (validado con la skill de visualización); un color nuevo se valida antes de agregarlo;
   - barras de 24 px de grosor como máximo;
   - rejilla de 1 px;
   - textos con tokens de texto, nunca con el color de la serie;
   - tooltip y vista de tabla.
5. **Animaciones:** cada una debe detenerse con «reducir movimiento».
6. **Lo que se descartó y no debe volver:** fotos de stock, auroras y brillos difusos, redes de partículas, y tarjetas centrales sobre fondo oscuro.
