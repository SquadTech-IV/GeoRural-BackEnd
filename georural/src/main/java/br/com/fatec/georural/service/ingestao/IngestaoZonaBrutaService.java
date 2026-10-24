package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConteudoRecebido;
import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IngestaoZonaBrutaService {

    private final ArquivoBrutoRepository arquivoBrutoRepository;
    private final MovimentacaoZonaRepository movimentacaoZonaRepository;
    private final ArmazenamentoZonaBruta armazenamento;

    @Transactional
    public ArquivoBruto registrar(ConjuntoDados conjunto,
                                  Usuario usuario,
                                  String nomeArquivo,
                                  FormatoRespostaApi formato,
                                  ConteudoRecebido conteudo,
                                  String origem) {

        LocalDateTime agora = LocalDateTime.now();
        String hash = sha256(conteudo.conteudo());
        String caminho = armazenamento.salvar(conjunto.getId(), nomeArquivo, conteudo.conteudo());

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