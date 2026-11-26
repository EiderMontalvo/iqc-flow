import { createContext, useContext } from 'react';
import type { ClaveTematica } from './tematicas';

/** Temática de temporada vigente para todo el panel (la usan los cajones y formularios para su fondo) */
export const ContextoTematica = createContext<ClaveTematica | null>(null);

export const useTematica = () => useContext(ContextoTematica);
