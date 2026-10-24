package br.com.fatec.georural.service.storage;

/**
 * Zonas do data lake. O bucket de cada zona vem de {@code georural.lake.bucket.*}.
 */
public enum Zona {
    BRUTA,
    QUARENTENA,
    TRATADA,
    PUBLICADA
}