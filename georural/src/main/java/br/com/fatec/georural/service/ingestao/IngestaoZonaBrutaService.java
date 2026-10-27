package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConteudoRecebido;
import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.ObjetoJaExisteException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.CaminhoZonaBrutaHelper;
import br.com.fatec.georural.service.storage.Zona;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class IngestaoZonaBrutaService {

    private final ArquivoBrutoRepository arquivoBrutoRepository;
    private final MovimentacaoZonaRepository movimentacaoZonaRepository;
    private final ArmazenamentoZonaPort armazenamento;

    @Transactional
    public ArquivoBruto registrar(ConjuntoDados conjunto,
                                  Usuario usuario,
                                  String nomeArquivo,
                                  FormatoRespostaApi formato,
                                  ConteudoRecebido conteudo,
                                  String origem) {

        LocalDateTime agora = LocalDateTime.now();
        byte[] bytes = conteudo.conteudo() == null ? new byte[0] : conteudo.conteudo();
        String hash = sha256(bytes);

        Long usuarioId = usuario != null ? usuario.getId() : null;
        String caminho = CaminhoZonaBrutaHelper.gerarCaminho(conjunto.getId(), agora, hash, nomeArquivo);
        Map<String, String> metadados = Map.of(
                CaminhoZonaBrutaHelper.META_HASH, hash,
                CaminhoZonaBrutaHelper.META_CONJUNTO_ID, String.valueOf(conjunto.getId()),
                CaminhoZonaBrutaHelper.META_USUARIO_ID, String.valueOf(usuarioId));

        // Grava no armazenamento de objetos da zona BRUTA (mesmo bucket/caminho que o upload e o
        // processamento usam). A zona e imutavel: se o objeto ja existe, seguimos com o registro.
        try {
            armazenamento.gravar(Zona.BRUTA, caminho, new ByteArrayInputStream(bytes), bytes.length, metadados);
        } catch (ObjetoJaExisteException e) {
            // objeto identico ja esta na zona bruta; mantem o registro apontando para o mesmo caminho
        }

        ArquivoBruto arquivo = new ArquivoBruto();
        arquivo.setConjunto(conjunto);
        arquivo.setUsuario(usuario);
        arquivo.setNome(nomeArquivo);
        arquivo.setFormato(formato != null ? formato.name() : null);
        arquivo.setPathArmazenamento(caminho);
        arquivo.setTamanhoBytes(conteudo.tamanhoBytes());
        arquivo.setStatus("RECEBIDO");
        arquivo.setHash(hash);
        arquivo.setDataUpload(agora);
        arquivo = arquivoBrutoRepository.save(arquivo);

        MovimentacaoZona mov = new MovimentacaoZona();
        mov.setArquivoBruto(arquivo);
        mov.setZona("BRUTA");
        mov.setStatus("RECEBIDO");
        mov.setObservacao("Ingestao via " + origem + " - " + conteudo.quantidadeRegistros() + " registro(s)");
        mov.setData(agora);
        movimentacaoZonaRepository.save(mov);

        return arquivo;
    }

    /** SHA-256 em hex */
    public static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel no ambiente", e);
        }
    }
}
