package com.seniorinsight.identity.adapters.out.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.seniorinsight.identity.TesteIntegracao;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** O {@code idosoId} do perfil só é preenchido quando está vazio. */
@TesteIntegracao
class PreencherIdosoIdIntegracaoTest {

    @Autowired
    RepositorioPerfis perfis;

    @Autowired
    TransactionTemplate transacao;

    @Test
    void preencheOIdosoIdVazioEAtualizaOMomento() {
        Perfil sem = guardar(Perfil.novoIdoso(UUID.randomUUID().toString(), "Maria", null, agora()));
        Perfil preenchido = sem.comIdosoId(UUID.randomUUID(), agora().plusSeconds(60));

        assertThat(preencher(preenchido)).isTrue();

        Perfil lido = perfis.buscarPorKeycloakSub(sem.keycloakSub()).orElseThrow();
        assertThat(lido.idosoId()).isEqualTo(preenchido.idosoId());
        assertThat(lido.atualizadoEm()).isEqualTo(preenchido.atualizadoEm());
    }

    @Test
    void naoSobrescreveUmIdosoIdJaGravado() {
        UUID gravado = UUID.randomUUID();
        Perfil com = guardar(Perfil.novoIdoso(UUID.randomUUID().toString(), "Maria", gravado, agora()));
        Perfil outro = com.comIdosoId(UUID.randomUUID(), agora().plusSeconds(60));

        assertThat(preencher(outro)).isFalse();

        assertThat(perfis.buscarPorKeycloakSub(com.keycloakSub()).orElseThrow().idosoId()).isEqualTo(gravado);
    }

    private Perfil guardar(Perfil perfil) {
        transacao.executeWithoutResult(status -> perfis.inserirSeAusente(perfil));
        return perfil;
    }

    private boolean preencher(Perfil perfil) {
        return Boolean.TRUE.equals(transacao.execute(status -> perfis.preencherIdosoId(perfil)));
    }

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
