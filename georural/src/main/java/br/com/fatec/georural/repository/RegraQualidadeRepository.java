package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.RegraQualidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegraQualidadeRepository extends JpaRepository<RegraQualidade, Long> {

    /** Regras ativas. */
    List<RegraQualidade> findByAtiva(String ativa);
}
