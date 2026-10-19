package br.com.fatec.georural.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import br.com.fatec.georural.entity.ArquivoBruto;


public interface ArquivoBrutoRepository extends JpaRepository<ArquivoBruto, Long> {
    Optional<ArquivoBruto> findByHash(String hash);

    List<ArquivoBruto> findByConjunto_IdOrderByDataUploadAsc(Long conjuntoId);
}