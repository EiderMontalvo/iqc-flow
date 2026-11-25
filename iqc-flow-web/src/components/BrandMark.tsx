/** Logo horizontal de IQC (blanco): va siempre sobre el negro de marca (carril, acceso, carga) */
export function BrandMark({ alto = 48 }: { alto?: number }) {
  return <img className="brand-mark" src={`${import.meta.env.BASE_URL}logo-iqc-horizontal.png`}
    alt="IQC Integrated Quality Certifications" height={alto} width={alto * 2} />;
}
