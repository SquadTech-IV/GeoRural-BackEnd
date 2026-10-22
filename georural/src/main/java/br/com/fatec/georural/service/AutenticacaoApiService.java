package br.com.fatec.georural.service;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AutenticacaoApiService {
    private final String chave;
    private final SecureRandom random = new SecureRandom();

    public AutenticacaoApiService(@Value("${georural.api.chave:${GEORURAL_API_CHAVE:}}") String chave) {
        this.chave = chave;
    }

    public String proteger(String token) {
        if (token == null || token.isBlank()) return null;
        if (chave.isBlank()) throw new RequisicaoInvalidaException("Configure GEORURAL_API_CHAVE no backend antes de salvar uma autenticação.");
        try {
            byte[] segredo = Base64.getDecoder().decode(chave);
            if (segredo.length != 32) throw new IllegalArgumentException();
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(segredo, "AES"), new GCMParameterSpec(128, iv));
            byte[] cifrado = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            String protegido = "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cifrado);
            if (protegido.length() > 200) {
                throw new RequisicaoInvalidaException("A autenticação criptografada excede os 200 caracteres permitidos em con_api_auth. Amplie o armazenamento antes de usar tokens maiores.");
            }
            return protegido;
        } catch (RequisicaoInvalidaException erro) {
            throw erro;
        } catch (Exception erro) {
            throw new IllegalStateException("Não foi possível proteger a autenticação da API.", erro);
        }
    }
}
