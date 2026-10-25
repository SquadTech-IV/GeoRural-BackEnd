package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Embargo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmbargoRepository extends JpaRepository<Embargo, Long> {
    Optional<Embargo> findFirstByOrderByVersaoDesc();
}
