package com.seniorinsight.identity.application.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.seniorinsight.identity.TesteIntegracao;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Primeiro acesso concorrente contra o PostgreSQL real: o unique de {@code keycloak_sub} decide quem cria. */
@TesteIntegracao
class ObterOuCriarPerfilIntegracaoTest {

    private static final int CHAMADAS = 8;

    @Autowired
    ObterOuCriarPerfil casoDeUso;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void chamadasSimultaneasCriamUmPerfilEPublicamUmEvento() throws Exception {
        String sub = UUID.randomUUID().toString();
        ObterOuCriarPerfil.Comando comando =
                new ObterOuCriarPerfil.Comando(sub, Set.of("familiar"), "Ana Souza", "ana", "ana@example.com");
        CountDownLatch largada = new CountDownLatch(1);
        Callable<Perfil> chamada = () -> {
            largada.await();
            return casoDeUso.executar(comando);
        };

        List<Perfil> perfis;
        try (ExecutorService executor = Executors.newFixedThreadPool(CHAMADAS)) {
            List<Future<Perfil>> futuros = IntStream.range(0, CHAMADAS)
                    .mapToObj(i -> executor.submit(chamada))
                    .toList();
            largada.countDown();
            perfis = new ArrayList<>();
            for (Future<Perfil> futuro : futuros) {
                perfis.add(futuro.get());
            }
        }

        assertThat(perfis).extracting(Perfil::id).containsOnly(perfis.getFirst().id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM perfil WHERE keycloak_sub = ?", Integer.class, sub))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'user.created' AND agregado_id = ?",
                Integer.class, perfis.getFirst().id())).isEqualTo(1);
    }
}
