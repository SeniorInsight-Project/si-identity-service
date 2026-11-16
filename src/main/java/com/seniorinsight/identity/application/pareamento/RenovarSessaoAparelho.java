package com.seniorinsight.identity.application.pareamento;

import java.util.Objects;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade.Tokens;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade.TokensRenovados;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.application.porta.saida.RepositorioSessoesAparelho;
import com.seniorinsight.identity.domain.pareamento.SessaoAparelho;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Renova os tokens da sessão do aparelho do idoso (pendência do ADR 0001).
 *
 * <p>O refresh token emitido no resgate tem {@code azp = si-identity-service}, então o app não consegue renovar direto
 * no Keycloak. Este caso de uso confere se a {@code sessao_aparelho} existe e segue ativa, renova com as credenciais
 * do serviço e garante que o {@code sub} do novo access token é o do perfil dono da sessão.
 */
@Service
public class RenovarSessaoAparelho {

    private static final Logger log = LoggerFactory.getLogger(RenovarSessaoAparelho.class);

    private final RepositorioSessoesAparelho sessoes;
    private final RepositorioPerfis perfis;
    private final ProvedorIdentidade provedor;

    public RenovarSessaoAparelho(RepositorioSessoesAparelho sessoes, RepositorioPerfis perfis,
                                 ProvedorIdentidade provedor) {
        this.sessoes = sessoes;
        this.perfis = perfis;
        this.provedor = provedor;
    }

    public Tokens executar(Comando comando) {
        SessaoAparelho sessao = sessoes.buscarPorId(comando.sessaoAparelhoId())
                .orElseThrow(SessaoNaoEncontrada::new);
        if (sessao.revogada()) {
            throw new RenovacaoNaoAutorizada("Sessão do aparelho foi revogada.");
        }
        Perfil perfil = perfis.buscarPorId(sessao.perfilId())
                .orElseThrow(SessaoNaoEncontrada::new);
        TokensRenovados renovados = provedor.renovarTokens(comando.refreshToken());
        if (!perfil.keycloakSub().equals(renovados.keycloakSub())) {
            log.warn("Tentativa de renovação da sessão {} com token de outro perfil (perfil {})",
                    sessao.id(), perfil.id());
            throw new RenovacaoNaoAutorizada("Refresh token não pertence ao perfil da sessão.");
        }
        return renovados.tokens();
    }

    public record Comando(UUID sessaoAparelhoId, String refreshToken) {

        public Comando {
            Objects.requireNonNull(sessaoAparelhoId, "sessaoAparelhoId");
            Objects.requireNonNull(refreshToken, "refreshToken");
        }
    }
}
