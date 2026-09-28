package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ArquivoDatalakeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ArquivoDatalakeItemRepository extends JpaRepository<ArquivoDatalakeItem, Long> {

    List<ArquivoDatalakeItem> findByArquivo_IdOrderById(Long arquivoId);
}