package br.com.fatec.georural.service.auditoria;

import br.com.fatec.georural.dto.response.auditoria.AuditoriaResponse;
import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.AuditoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** US#20.02 - Consulta da trilha de auditoria com filtros por usuario, acao e periodo. */
@Service
@RequiredArgsConstructor
public class AuditoriaConsultaService {

    private final AuditoriaRepository repository;

    @Transactional(readOnly = true)
    public Page<AuditoriaResponse> consultar(Long usuarioId, String acao,
                                             LocalDate de, LocalDate ate, Pageable pageable) {
        if (de != null && ate != null && ate.isBefore(de)) {
            throw new RequisicaoInvalidaException("A data final não pode ser anterior à data inicial.");
        }

        Specification<Auditoria> filtro = (r, q, cb) -> cb.conjunction();
        if (usuarioId != null) {
            filtro = filtro.and((r, q, cb) -> cb.equal(r.get("usuario").get("id"), usuarioId));
        }
        if (acao != null && !acao.isBlank()) {
            String op = acao.trim().toUpperCase();
            filtro = filtro.and((r, q, cb) -> cb.equal(r.get("operacao"), op));
        }
        if (de != null) {
            filtro = filtro.and((r, q, cb) ->
                    cb.greaterThanOrEqualTo(r.get("dataHora"), de.atStartOfDay()));
        }
        if (ate != null) {
            filtro = filtro.and((r, q, cb) ->
                    cb.lessThan(r.get("dataHora"), ate.plusDays(1).atStartOfDay()));
        }
        return repository.findAll(filtro, pageable).map(AuditoriaResponse::de);
    }
}
