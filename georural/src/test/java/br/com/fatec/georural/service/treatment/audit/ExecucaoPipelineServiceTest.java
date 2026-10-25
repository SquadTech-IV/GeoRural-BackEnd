package br.com.fatec.georural.service.treatment.audit;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecucaoPipelineServiceTest {

    @Mock
    private ExecucaoPipelineRepository repository;

    @InjectMocks
    private ExecucaoPipelineService service;

    @Test
    @DisplayName("Deve finalizar execução com sucesso quando a invariante (recebidos = aprovados + rejeitados) for válida")
    void deveFinalizarComSucessoQuandoInvarianteValida() {
        ExecucaoPipeline execucao = new ExecucaoPipeline();
        execucao.setId(10L);
        execucao.setInicio(LocalDateTime.now().minusMinutes(2));

        when(repository.save(any(ExecucaoPipeline.class))).thenAnswer(i -> i.getArgument(0));

        ExecucaoPipeline resultado = service.finalizarComSucesso(execucao, 2158L, 8L);

        assertEquals(2158L, resultado.getRegistrosRecebidos());
        assertEquals(2150L, resultado.getRegistrosAprovados());
        assertEquals(8L, resultado.getRegistrosRejeitados());
        assertEquals("COM_RESSALVA", resultado.getStatus());
        assertNotNull(resultado.getFim());
        assertTrue(resultado.getDuracaoSeg() >= 0);
    }

    @Test
    @DisplayName("Deve definir status CONCLUIDA quando não houver registros rejeitados")
    void deveDefinirConcluidaQuandoZeroRejeitados() {
        ExecucaoPipeline execucao = new ExecucaoPipeline();
        execucao.setId(11L);
        execucao.setInicio(LocalDateTime.now());

        when(repository.save(any(ExecucaoPipeline.class))).thenAnswer(i -> i.getArgument(0));

        ExecucaoPipeline resultado = service.finalizarComSucesso(execucao, 500L, 0L);

        assertEquals("CONCLUIDA", resultado.getStatus());
        assertEquals(500L, resultado.getRegistrosAprovados());
    }

    @Test
    @DisplayName("Deve lançar EstadoInconsistenteException quando a soma violar o total recebido")
    void deveLancarExcecaoQuandoInvarianteInvalida() {
        ExecucaoPipeline execucao = new ExecucaoPipeline();
        execucao.setId(12L);

        // Simulando divergência matemática proposital
        assertThrows(EstadoInconsistenteException.class, () -> 
            service.finalizarComSucesso(execucao, 100L, -5L)
        );
    }

    @Test
    @DisplayName("Deve registrar falha técnica sem marcar rejeições de dados falsas")
    void deveRegistrarFalhaTecnicaSemCriarRejeicaoFalsa() {
        ExecucaoPipeline execucao = new ExecucaoPipeline();
        execucao.setId(13L);
        execucao.setInicio(LocalDateTime.now());

        when(repository.save(any(ExecucaoPipeline.class))).thenAnswer(i -> i.getArgument(0));

        RuntimeException erroConexao = new RuntimeException("Falha de comunicação com BD");
        service.registrarFalhaTecnica(execucao, erroConexao);

        assertEquals("FALHOU", execucao.getStatus());
        assertNotNull(execucao.getFim());
        verify(repository, times(1)).save(execucao);
    }
}