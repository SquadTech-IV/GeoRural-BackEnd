package br.com.fatec.georural.service.usuario;

import br.com.fatec.georural.dto.response.usuario.PerfilAcessoResponse;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.PerfilRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UsuarioConsultaService {

    private static final Set<String> STATUS_VALIDOS = Set.of("S", "N");

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(String busca, Long perfilId, String statusRaw, Pageable pageable) {
        String buscaNormalizada = (busca == null || busca.trim().isEmpty()) ? null : busca.trim();

        String statusNormalizado = null;
        if (statusRaw != null && !statusRaw.trim().isEmpty()) {
            statusNormalizado = statusRaw.trim().toUpperCase();
            if (!STATUS_VALIDOS.contains(statusNormalizado)) {
                throw new RequisicaoInvalidaException("Status inválido: '" + statusRaw + "'. Valores aceitos: S ou N.");
            }
        }

        Page<Usuario> pagina = usuarioRepository.consultarUsuariosPaginado(
                buscaNormalizada,
                perfilId,
                statusNormalizado,
                pageable
        );

        return pagina.map(UsuarioResponse::de);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse detalhar(Long id) {
        Usuario usuario = usuarioRepository.findByIdComPerfil(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + id));
        return UsuarioResponse.de(usuario);
    }

    @Transactional(readOnly = true)
    public List<PerfilAcessoResponse> listarPerfis() {
        return perfilRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(PerfilAcessoResponse::de)
                .toList();
    }
}