package com.lab.logistica.importacao.dominio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntregaHistoricoRepository extends JpaRepository<EntregaHistorico, Long> {

    List<EntregaHistorico> findByCaminhaoAndConsumoKmLIsNotNull(String caminhao);
}
