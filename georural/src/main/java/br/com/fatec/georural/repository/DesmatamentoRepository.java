package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Desmatamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesmatamentoRepository extends JpaRepository<Desmatamento, Long> {
}
