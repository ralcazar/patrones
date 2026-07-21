package com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Único adaptador de escritura/lectura JPA del agregado {@code datos_negocio} (ver AdaptadorDatosNegocio). */
public interface DatosNegocioJpaRepository extends JpaRepository<DatosNegocioEntity, UUID> {
    Optional<DatosNegocioEntity> findByExternalId(String externalId);

    // Selección por lote para la purga de adjuntos: UNA query para todos los
    // externalIds del corte, en vez de resolver externalId a externalId (N+1). Solo
    // los que aún no tienen purgado_en sellado, para que una segunda pasada no
    // reprocese lo ya purgado.
    @Query(value = "SELECT datosnegocio_id FROM datos_negocio "
            + "WHERE external_id IN :externalIds AND purgado_en IS NULL",
            nativeQuery = true)
    List<String> idsPorExternalIdsSinPurgar(@Param("externalIds") List<String> externalIds);

    // @Transactional explícito: igual que en DocumentoNegocioJpaRepository#deleteByDatosnegocioId,
    // una @Modifying nativa no abre transacción propia; en producción corre dentro de la
    // @Transactional de ServicioPurgarAdjuntos (se une a ella, REQUIRED); esto la hace robusta
    // también si se invoca de forma aislada (p. ej. en tests de AdaptadorDatosNegocio).
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE datos_negocio SET purgado_en = :purgadoEn WHERE datosnegocio_id = :id",
            nativeQuery = true)
    void sellarPurgadoEn(@Param("id") UUID id, @Param("purgadoEn") Instant purgadoEn);
}
