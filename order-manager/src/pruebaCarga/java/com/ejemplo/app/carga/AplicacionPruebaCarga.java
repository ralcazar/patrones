package com.ejemplo.app.carga;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.ejemplo.app.infraestructure.sagas.eventos.ConsumidorRespuestaSecundaria2;

/**
 * Arranque Spring Boot de cada "pod" del harness de pruebas de carga. NO
 * reutiliza {@code com.ejemplo.app.OrderManagerApplication} (ese
 * {@code @SpringBootApplication} haría component-scan + auto-configuración
 * de Kafka sin que podamos excluir nada) sino que replica sus tres
 * meta-anotaciones ({@code @SpringBootConfiguration + @EnableAutoConfiguration
 * + @ComponentScan}) a mano, con dos diferencias deliberadas:
 *
 * <ul>
 *   <li>{@code @EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)}:
 *       sin Kafka en el perfil de carga (decisión de diseño del plan de
 *       pruebas de carga: la secundaria 2 se simula con
 *       {@link com.ejemplo.app.carga.mocks.SimuladorRespuestaSecundaria2}).</li>
 *   <li>{@code @ComponentScan} EXCLUYE explícitamente
 *       {@link ConsumidorRespuestaSecundaria2} (el {@code @KafkaListener}
 *       real): así no hace falta tocar esa clase de producción (nada de
 *       {@code @Profile("!carga")} en ella) para que no se registre como
 *       bean en el perfil de carga.</li>
 * </ul>
 *
 * <p>Como esta clase vive en {@code com.ejemplo.app.carga} (no en la raíz
 * {@code com.ejemplo.app} de {@code OrderManagerApplication}), Spring Boot no
 * puede inferir por convención el paquete base de entidades/repositorios JPA
 * ({@code @AutoConfigurationPackage} registraría {@code com.ejemplo.app.carga},
 * no {@code com.ejemplo.app.infraestructure}): de ahí el
 * {@code @EntityScan}/{@code @EnableJpaRepositories} explícitos.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
@EntityScan(basePackages = "com.ejemplo.app.infraestructure")
@EnableJpaRepositories(basePackages = "com.ejemplo.app.infraestructure")
@ComponentScan(
        basePackages = {
                "com.ejemplo.app.business",
                "com.ejemplo.app.infraestructure",
                "com.ejemplo.app.carga"
        },
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ConsumidorRespuestaSecundaria2.class))
public class AplicacionPruebaCarga {
}
