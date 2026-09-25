package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Versao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersaoRepository extends JpaRepository<Versao, Long> {
}
