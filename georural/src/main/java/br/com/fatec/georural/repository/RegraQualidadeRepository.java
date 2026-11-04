package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.RegraQualidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegraQualidadeRepository extends JpaRepository<RegraQualidade, Long> {

    // regras ativas
    List<RegraQualidade> findByAtiva(String ativa);

    // regra pelo nome cadastrado no V2
    Optional<RegraQualidade> findByNome(String nome);
}
