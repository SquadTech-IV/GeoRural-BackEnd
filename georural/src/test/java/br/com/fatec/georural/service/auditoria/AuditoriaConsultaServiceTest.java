package br.com.fatec.georural.service.auditoria;

import br.com.fatec.georural.dto.response.auditoria.AuditoriaResponse;
import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.AuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditoriaConsultaServiceTest {

    private final AuditoriaRepository repository = mock(AuditoriaRepository.class);
    private final AuditoriaConsultaService service = new AuditoriaConsultaService(repository);

    @Test
    @DisplayName("Converte os registros da pagina para AuditoriaResponse")
    @SuppressWarnings("unchecked")
    void consultar_converteResposta() {
        Auditoria a = new Auditoria();
        a.setOperacao("DOWNLOAD");
        a.setEntidadeAfetada("ARQUIVO_BRUTO");
        a.setRegistroId(5L);
        a.setUsuarioNome("Auditor");
        a.setDataHora(LocalDateTime.of(2026, 11, 20, 10, 0));
        Pageable p = PageRequest.of(0, 20);
        when(repository.findAll(any(Specification.class), eq(p))).thenReturn(new PageImpl<>(List.of(a), p, 1));

        var pagina = service.consultar(null, "download", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), p);

        assertEquals(1, pagina.getTotalElements());
        AuditoriaResponse r = pagina.getContent().get(0);
        assertEquals("DOWNLOAD", r.acao());
        assertEquals("ARQUIVO_BRUTO", r.entidade());
        assertEquals(5L, r.registroId());
        assertEquals("Auditor", r.usuarioNome());
    }

    @Test
    @DisplayName("Periodo invertido retorna requisicao invalida")
    void consultar_periodoInvertido() {
        assertThrows(RequisicaoInvalidaException.class, () -> service.consultar(
                null, null, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), PageRequest.of(0, 20)));
        verifyNoInteractions(repository);
    }
}
