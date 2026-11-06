-- Volcado FICTICIO con la estructura de la plataforma anterior (phpMyAdmin). No contiene datos reales.
-- Casos: cliente duplicado por RUC, cliente sin RUC, cliente de Ecuador, cliente eliminado, prospecto,
-- carga duplicada, certificado eliminado con el mismo código, prueba, norma desconocida, anulado, suspendido,
-- letra de edición 2025, certificado integrado de India (un código, dos normas) y QR huérfano.

CREATE TABLE `Clientes` (
  `idCliente` varchar(50) NOT NULL,
  `ruc` text DEFAULT NULL,
  `razonSocial` varchar(255) NOT NULL,
  `direccion` varchar(255) NOT NULL DEFAULT '',
  `representante_legal` text DEFAULT NULL,
  `fechaCreacion` timestamp NOT NULL DEFAULT current_timestamp(),
  `fechaModificacion` timestamp NOT NULL DEFAULT current_timestamp(),
  `estado` int(11) NOT NULL DEFAULT 2
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

INSERT INTO `Clientes` (`idCliente`, `ruc`, `razonSocial`, `direccion`, `representante_legal`, `fechaCreacion`, `fechaModificacion`, `estado`) VALUES
('cli-uno', '20600000013', 'Constructora Uno S.A.C.', 'Av. Ficticia 123 - Lima', 'Persona Ficticia Uno', '2021-01-01 10:00:00', '2021-01-01 10:00:00', 2),
('cli-uno-bis', '20600000013 ', 'CONSTRUCTORA UNO SAC', '', NULL, '2022-01-01 10:00:00', '2022-01-01 10:00:00', 2),
('cli-dos', '-', 'Minera Dos S.A.C.', '', NULL, '2021-02-01 10:00:00', '2021-02-01 10:00:00', 2),
('cli-tres', '0991234567001', 'Ecuatoriana Tres S.A.', 'Calle Ficticia, Guayaquil', NULL, '2021-03-01 10:00:00', '2021-03-01 10:00:00', 2),
('cli-borrado', '20600000021', 'Borrada S.A.C.', '', NULL, '2021-04-01 10:00:00', '2021-04-01 10:00:00', 1),
('cli-prospecto', '20600000030', 'Sin Certificados S.A.C.', '', NULL, '2021-05-01 10:00:00', '2021-05-01 10:00:00', 2);

INSERT INTO `Certificaciones` (`idCertificacion`, `idCliente`, `status`, `norma`, `codigo`, `alcance`, `urlCertificado`, `pais`, `fechaCertificacion`, `fechaExpiracion`, `fechaOtorgamiento`, `fechaSeguimientoUno`, `fechaSeguimientoDos`, `acreditadoras`, `fechaModificacion`, `estado`) VALUES
('k1', 'cli-uno', '2', '37001:2016', 'PE-INA-00120250111', 'Construcción de obras civiles', 'https://storage.googleapis.com/prueba/k1.pdf', 'Perú', '2025-01-10', '2099-01-10', '2025-01-10', '2026-01-10', '2098-01-10', 'INACAL', '2025-01-12 09:00:00', 2),
('k2', 'cli-uno-bis', '2', ' 9001:2015A', 'PE-INA-00120250211', 'Construcción de obras civiles', 'https://storage.googleapis.com/prueba/k2.pdf', 'Perú', '2025-01-10', '2099-01-10', '0000-00-00', '2026-01-10', '2098-01-10', '', '2025-01-12 09:00:00', 2),
('k3', 'cli-uno', '2', '37001:2016', 'PE-INA-00120250111', 'Construcción', 'https://storage.googleapis.com/prueba/k3.pdf', 'Perú', '2025-01-10', '2099-01-10', '2025-01-10', '2026-01-10', '2098-01-10', 'INACAL', '2025-01-11 09:00:00', 2),
('k4', 'cli-dos', '2', '37001:2016', 'PE-SAE-00220260111A', 'Minería', NULL, 'Perú', '2026-03-30', '2029-03-29', '2026-03-30', '2027-03-29', '2028-03-29', 'SERVICIO DE ACREDITACIÓN ECUATORIANO', '2026-03-30 09:00:00', 2),
('k5', 'cli-tres', '2', '45001:2018', 'PER/IMS-QEO/JAS-C0001/RC1/0001', 'Servicios', '', 'Ecuador', '2024-05-01', '2027-04-30', '0000-00-00', '2025-04-30', '2026-04-30', 'IAF, JAS-ANZ y IQCGLOBAL', '2024-05-02 09:00:00', 2),
('k6', 'cli-tres', '2', '14001:2015', 'PER/IMS-QEO/JAS-C0001/RC1/0001', 'Servicios', '', 'Ecuador', '2024-05-01', '2027-04-30', '0000-00-00', '2025-04-30', '2026-04-30', 'IAF, JAS-ANZ y IQCGLOBAL', '2024-05-02 09:00:00', 2),
('k7', 'cli-uno', '1', '45001:2018', 'PE-INA-00120210511', 'Construcción', NULL, 'Perú', '2021-02-01', '2024-01-31', '2021-02-01', '0000-00-00', '0000-00-00', 'INACAL', '2023-01-01 09:00:00', 2),
('k8', 'cli-uno', '2', 'asdasd', 'asdasd', 'prueba', NULL, 'Perú', '2024-01-01', '2024-01-01', NULL, NULL, NULL, '', '2024-01-01 09:00:00', 2),
('k9', 'cli-uno', '2', '37001:2016', 'PE-INA-00120250111', 'Construcción', NULL, 'Perú', '2025-01-10', '2099-01-10', '2025-01-10', NULL, NULL, 'INACAL', '2025-01-10 09:00:00', 1),
('k10', 'cli-dos', '2', '4005-011', 'PE00011154', 'Viajes', NULL, 'Perú', '2023-11-16', '2026-11-16', NULL, NULL, NULL, '', '2023-11-16 09:00:00', 2),
('k11', 'cli-dos', '4', '14001:2015', 'PE-SAE-00220260311', 'Minería', NULL, 'Perú', '2026-03-30', '2029-03-29', '2026-03-30', '2027-03-29', '2028-03-29', 'SERVICIO DE ACREDITACIÓN ECUATORIANO', '2026-03-30 09:00:00', 2);

INSERT INTO `generarQR` (`codigoQR`, `idqr`, `idCertificacion`, `encriptacionID`, `estado`, `fecha_register`, `fecha_modi`) VALUES
(1, 'qr-1', 'k1', 'qr-1a', 2, '2025-01-12 10:00:00', NULL),
(2, 'qr-2', 'k3', 'qr-2b', 2, '2025-01-11 10:00:00', NULL),
(3, 'qr-3', 'k9', 'qr-3c', 1, '2025-01-10 10:00:00', NULL),
(4, 'qr-4', 'k5', 'qr-4d', 2, '2024-05-02 10:00:00', NULL),
(5, 'qr-5', 'no-existe', 'qr-5e', 2, '2024-05-02 10:00:00', NULL);
