package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ArquivoDatalakeConteudo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArquivoDatalakeConteudoRepository
        extends JpaRepository<ArquivoDatalakeConteudo, Long> {
}