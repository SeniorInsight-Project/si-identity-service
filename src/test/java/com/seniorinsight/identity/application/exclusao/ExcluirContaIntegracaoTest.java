package com.seniorinsight.identity.application.exclusao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.seniorinsight.identity.TesteIntegracao;
import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento.Consumo;
import com.seniorinsight.identity.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.application.porta.saida.RepositorioSessoesAparelho;
import com.seniorinsight.identity.application.porta.saida.RepositorioTitularesExcluidos;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;
import com.seniorinsight.identity.domain.pareamento.Plataforma;
import com.seniorinsight.identity.domain.pareamento.SessaoAparelho;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** A exclusão de ponta a ponta no PostgreSQL, na outbox e no Redis reais; só o Keycloak é simulado. */
@TesteIntegracao
class ExcluirContaIntegracaoTest {

    private static final Duration RETENCAO = Duration.ofHours(1);

    @Autowired
    RepositorioPedidosExclusao pedidos;

    @Autowired
    RepositorioTitularesExcluidos excluidos;

    @Autowired
    RepositorioPerfis perfis;

    @Autowired
    RepositorioSessoesAparelho sessoes;

    @Autowired
    RepositorioCodigosPareamento codigos;

    @Autowired
    PublicadorEventos publicador;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    private final ProvedorIdentidade keycloak = mock(ProvedorIdentidade.class);

    private ExcluirConta casoDeUso() {
        return new ExcluirConta(pedidos, keycloak, excluidos, perfis, sessoes, codigos, publicador, transacao);
    }

    @Test
    void familiarTemUsuarioPerfilSessoesECodigosApagadosEAConfirmacaoSaiUmaVez() {
        String sub = UUID.randomUUID().toString();
        Perfil familiar = Perfil.novoFamiliar(sub, "Ana", "ana@example.com", agora());
        guardar(familiar);
        CodigoPareamento codigo = new CodigoPareamento(CodigoPareamento.sortear(), sub, "Maria", null,
                Instant.now().plus(Duration.ofHours(1)));
        codigos.salvar(codigo, RETENCAO);
        PedidoExclusao pedido = new PedidoExclusao(UUID.randomUUID(), TipoPerfil.FAMILIAR, sub);

        casoDeUso().executar(pedido);

        verify(keycloak).excluirUsuario(sub);
        assertThat(perfis.buscarPorKeycloakSub(sub)).isEmpty();
        assertThat(codigos.consumir(codigo.codigo(), Instant.now(), RETENCAO)).isInstanceOf(Consumo.Inexistente.class);
        assertThat(confirmacoes(pedido)).isEqualTo(1);
        assertThat(excluido(sub)).isTrue();
        assertThat(situacao(pedido)).isEqualTo("CONCLUIDO");
    }

    @Test
    void idosoTemUsuarioPerfilESessoesApagadosEAConfirmacaoSaiUmaVez() {
        String sub = UUID.randomUUID().toString();
        Perfil idoso = Perfil.novoIdoso(sub, "Maria", UUID.randomUUID(), agora());
        guardar(idoso);
        transacao.executeWithoutResult(status -> {
            sessoes.registrar(SessaoAparelho.nova(idoso.id(), "aparelho-1", Plataforma.ANDROID, "sub-ana", agora()));
            sessoes.registrar(SessaoAparelho.nova(idoso.id(), "aparelho-2", Plataforma.IOS, "sub-ana", agora()));
        });
        PedidoExclusao pedido = new PedidoExclusao(UUID.randomUUID(), TipoPerfil.IDOSO, sub);

        casoDeUso().executar(pedido);

        verify(keycloak).excluirUsuario(sub);
        assertThat(perfis.buscarPorKeycloakSub(sub)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessao_aparelho WHERE perfil_id = ?", Integer.class,
                idoso.id())).isZero();
        assertThat(confirmacoes(pedido)).isEqualTo(1);
    }

    @Test
    void semPerfilParaOSubAindaConfirma() {
        PedidoExclusao pedido = new PedidoExclusao(UUID.randomUUID(), TipoPerfil.FAMILIAR, UUID.randomUUID().toString());

        casoDeUso().executar(pedido);

        verify(keycloak).excluirUsuario(pedido.titularSub());
        assertThat(confirmacoes(pedido)).isEqualTo(1);
        assertThat(excluido(pedido.titularSub())).isTrue();
    }

    @Test
    void reentregaDoMesmoProtocoloNaoChamaOKeycloakDeNovoNemPublicaDeNovo() {
        PedidoExclusao pedido = new PedidoExclusao(UUID.randomUUID(), TipoPerfil.IDOSO, UUID.randomUUID().toString());

        casoDeUso().executar(pedido);
        casoDeUso().executar(pedido);

        verify(keycloak, times(1)).excluirUsuario(pedido.titularSub());
        assertThat(confirmacoes(pedido)).isEqualTo(1);
    }

    @Test
    void keycloakForaDoArNaoConfirmaEQuandoVoltaOMesmoPedidoConfirma() {
        String sub = UUID.randomUUID().toString();
        Perfil familiar = Perfil.novoFamiliar(sub, "Ana", null, agora());
        guardar(familiar);
        PedidoExclusao pedido = new PedidoExclusao(UUID.randomUUID(), TipoPerfil.FAMILIAR, sub);
        doThrow(new IdentidadeIndisponivel("fora do ar", null)).doNothing().when(keycloak).excluirUsuario(sub);

        assertThatThrownBy(() -> casoDeUso().executar(pedido)).isInstanceOf(IdentidadeIndisponivel.class);

        assertThat(situacao(pedido)).isEqualTo("PENDENTE");
        assertThat(confirmacoes(pedido)).isZero();
        assertThat(perfis.buscarPorKeycloakSub(sub)).isPresent();
        assertThat(excluido(sub)).isFalse();

        casoDeUso().executar(pedido);

        assertThat(situacao(pedido)).isEqualTo("CONCLUIDO");
        assertThat(confirmacoes(pedido)).isEqualTo(1);
        assertThat(perfis.buscarPorKeycloakSub(sub)).isEmpty();
    }

    private void guardar(Perfil perfil) {
        transacao.executeWithoutResult(status -> perfis.inserirSeAusente(perfil));
    }

    private int confirmacoes(PedidoExclusao pedido) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'lgpd.erasure.completed' AND agregado_id = ?
                """, Integer.class, pedido.protocolo());
    }

    private String situacao(PedidoExclusao pedido) {
        return jdbc.queryForObject("SELECT situacao FROM exclusao_pedido WHERE protocolo = ?", String.class,
                pedido.protocolo());
    }

    private boolean excluido(String sub) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM titular_excluido WHERE chave = sha256(convert_to(?, 'UTF8')))",
                Boolean.class, sub));
    }

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
