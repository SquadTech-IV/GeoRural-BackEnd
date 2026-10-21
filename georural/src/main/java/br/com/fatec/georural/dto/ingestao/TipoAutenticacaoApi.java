package br.com.fatec.georural.dto.ingestao;

public enum TipoAutenticacaoApi {

    NENHUMA,
    API_KEY,
    BASIC;

    /** Le o prefixo e resolve o tipo. */
    public static TipoAutenticacaoApi doCadastro(String conApiAuth) {
        if (conApiAuth == null || conApiAuth.isBlank()) return NENHUMA;
        String v = conApiAuth.trim().toUpperCase();
        if (v.startsWith("APIKEY") || v.startsWith("API_KEY")) return API_KEY;
        if (v.startsWith("BASIC")) return BASIC;
        if (v.equals("NONE") || v.equals("NENHUMA")) return NENHUMA;
        return NENHUMA;
    }
}