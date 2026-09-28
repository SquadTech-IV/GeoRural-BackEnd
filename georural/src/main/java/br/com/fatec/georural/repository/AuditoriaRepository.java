package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
}
