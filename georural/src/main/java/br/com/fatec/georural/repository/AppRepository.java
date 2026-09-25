package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.App;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppRepository extends JpaRepository<App, Long> {
}
