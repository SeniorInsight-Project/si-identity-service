package com.seniorinsight.identity.adapters.out.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.seniorinsight.identity.TesteIntegracao;
import com.seniorinsight.identity.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.application.porta.saida.RepositorioSessoesAparelho;
import com.seniorinsight.identity.application.porta.saida.RepositorioTitularesExcluidos;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.exclusao.SituacaoPedido;
import com.seniorinsight.identity.domain.pareamento.Plataforma;
import com.seniorinsight.identity.domain.pareamento.SessaoAparelho;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Tabelas e exclusões da HU-022 contra o PostgreSQL real. */
@TesteIntegracao
class ExclusaoPersistenciaIntegracaoTest {

    @Autowired
    RepositorioPedidosExclusao pedidos;

    @Autowired
    RepositorioTitularesExcluidos excluidos;

    @Autowired
    RepositorioPerfis perfis;

    @Autowired
    RepositorioSessoesAparelho sessoes;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void pedidoNovoFicaPendenteComOSubERegistrarDeNovoNaoMudaNada() {
        PedidoExclusao pedido = pedido();

        assertThat(registrar(pedido)).isEqualTo(SituacaoPedido.PENDENTE);
        assertThat(registrar(pedido)).isEqualTo(SituacaoPedido.PENDENTE);

        assertThat(jdbc.queryForMap("SELECT tipo_titular, titular_sub, situacao FROM exclusao_pedido WHERE protocolo = ?",
                pedido.protocolo()))
                .containsEntry("tipo_titular", "FAMILIAR")
                .containsEntry("titular_sub", pedido.titularSub())
                .containsEntry("situacao", "PENDENTE");
    }

    @Test
    void concluirMarcaOPedidoEApagaOSubDaTabelaEQuemChegaDepoisNaoConclui() {
        PedidoExclusao pedido = pedido();
        registrar(pedido);

        assertThat(concluir(pedido)).isTrue();
        assertThat(concluir(pedido)).isFalse();

        assertThat(registrar(pedido)).isEqualTo(SituacaoPedido.CONCLUIDO);
        assertThat(jdbc.queryForMap("SELECT titular_sub, situacao FROM exclusao_pedido WHERE protocolo = ?",
                pedido.protocolo()))
                .containsEntry("titular_sub", null)
                .containsEntry("situacao", "CONCLUIDO");
    }

    @Test
    void titularExcluidoEhGuardadoSoComoHashEConsultavelPeloSub() {
        String sub = UUID.randomUUID().toString();

        assertThat(excluido(sub)).isFalse();
        transacao.executeWithoutResult(status -> excluidos.registrarExclusao(sub));
        transacao.executeWithoutResult(status -> excluidos.registrarExclusao(sub));

        assertThat(excluido(sub)).isTrue();
        assertThat(excluido(UUID.randomUUID().toString()))
                .isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM titular_excluido WHERE chave = sha256(convert_to(?, 'UTF8'))",
                Integer.class, sub)).isEqualTo(1);
    }

    @Test
    void bloquearEVerificarForaDeTransacaoEhRecusado() {
        assertThatThrownBy(() -> excluidos.bloquearEVerificarExclusao("sub")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void excluirOPerfilEAsSessoesDeAparelho() {
        Perfil perfil = Perfil.novoIdoso(UUID.randomUUID().toString(), "Maria", UUID.randomUUID(),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        Perfil outro = Perfil.novoIdoso(UUID.randomUUID().toString(), "Joana", null,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        transacao.executeWithoutResult(status -> {
            perfis.inserirSeAusente(perfil);
            perfis.inserirSeAusente(outro);
            sessoes.registrar(sessao(perfil, "aparelho-1"));
            sessoes.registrar(sessao(perfil, "aparelho-2"));
            sessoes.registrar(sessao(outro, "aparelho-1"));
        });

        transacao.executeWithoutResult(status -> {
            sessoes.excluirDoPerfil(perfil.id());
            perfis.excluir(perfil.id());
        });

        assertThat(perfis.buscarPorKeycloakSub(perfil.keycloakSub())).isEmpty();
        assertThat(sessoesDoPerfil(perfil)).isZero();
        assertThat(perfis.buscarPorKeycloakSub(outro.keycloakSub())).isPresent();
        assertThat(sessoesDoPerfil(outro)).isEqualTo(1);
    }

    private boolean concluir(PedidoExclusao pedido) {
        return Boolean.TRUE.equals(transacao.execute(status -> pedidos.concluir(pedido.protocolo())));
    }

    private boolean excluido(String sub) {
        return Boolean.TRUE.equals(transacao.execute(status -> excluidos.bloquearEVerificarExclusao(sub)));
    }

    private SituacaoPedido registrar(PedidoExclusao pedido) {
        return transacao.execute(status -> pedidos.registrarPendente(pedido));
    }

    private int sessoesDoPerfil(Perfil perfil) {
        return jdbc.queryForObject("SELECT count(*) FROM sessao_aparelho WHERE perfil_id = ?", Integer.class,
                perfil.id());
    }

    private static SessaoAparelho sessao(Perfil perfil, String aparelho) {
        return SessaoAparelho.nova(perfil.id(), aparelho, Plataforma.ANDROID, "sub-ana", Instant.now());
    }

    private static PedidoExclusao pedido() {
        return new PedidoExclusao(UUID.randomUUID(), TipoPerfil.FAMILIAR, UUID.randomUUID().toString());
    }
}
