package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Tabla hija de {@code datos_negocio}: los {@link com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio} de una tramitación. */
public interface DocumentoNegocioJpaRepository extends JpaRepository<DocumentoNegocioEntity, DocumentoNegocioEntityId> {
    List<DocumentoNegocioEntity> findByDatosnegocioIdOrderBySecuenciaAsc(UUID datosnegocioId);

    // @Transactional explícito: a diferencia de save()/deleteById() (heredados de SimpleJpaRepository,
    // ya transaccionales por defecto), una query derivada delete...By como esta NO abre transacción
    // propia si se invoca fuera de una ya activa (lanzaría TransactionRequiredException). En producción
    // siempre corre dentro de la @Transactional de ServicioPurgarDatosNegocioHuerfanos (se une a ella,
    // REQUIRED); esto solo la hace robusta también si se invoca de forma aislada (p. ej. en tests).
    /** Borrado explícito de las hijas (sin ON DELETE CASCADE, ver CLAUDE.md), antes del datos_negocio padre. */
    @Transactional
    void deleteByDatosnegocioId(UUID datosnegocioId);
}
