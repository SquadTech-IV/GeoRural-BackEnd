package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ColetaApi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ColetaApiRepository extends JpaRepository<ColetaApi, Long> {
    Optional<ColetaApi> findByArquivoBruto_Id(Long arquivoBrutoId);
}
