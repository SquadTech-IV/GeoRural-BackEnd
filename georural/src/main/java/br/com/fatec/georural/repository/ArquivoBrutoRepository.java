package br.com.fatec.georural.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import br.com.fatec.georural.entity.ArquivoBruto;

public interface ArquivoBrutoRepository extends JpaRepository<ArquivoBruto, Long> {
    Optional<ArquivoBruto> findByHash(String hash);
}