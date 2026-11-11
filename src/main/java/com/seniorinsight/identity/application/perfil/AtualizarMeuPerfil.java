package com.seniorinsight.identity.application.perfil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edição do próprio perfil (PUT /v1/me). O nome é gravado no perfil local e no Keycloak, pela mesma service account
 * do pareamento. A chamada ao Keycloak fica dentro da transação: se ela falhar, o nome local volta ao anterior e os
 * dois lados continuam iguais. O realm exige nome e sobrenome, por isso o nome precisa ter ao menos duas palavras. E-mail e credenciais são do Keycloak e não mudam por aqui.
 */
@Service
public class AtualizarMeuPerfil {

    private final RepositorioPerfis repositorio;
    private final ProvedorIdentidade provedorIdentidade;

    public AtualizarMeuPerfil(RepositorioPerfis repositorio, ProvedorIdentidade provedorIdentidade) {
        this.repositorio = repositorio;
        this.provedorIdentidade = provedorIdentidade;
    }

    @Transactional
    public Perfil executar(Comando comando) {
        String nome = comando.nome().strip();
        if (nome.split("\s+").length < 2) {
            throw new NomeInvalido();
        }
        Perfil atual = repositorio.buscarPorKeycloakSub(comando.keycloakSub())
                .orElseThrow(() -> new PerfilNaoEncontrado(comando.keycloakSub()));
        // Microssegundos: a precisão do TIMESTAMPTZ, para o perfil devolvido ser igual ao relido do banco depois.
        Perfil atualizado = atual.comNome(nome, Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.atualizarNome(atualizado);
        provedorIdentidade.atualizarNome(atualizado.keycloakSub(), atualizado.nome());
        return atualizado;
    }

    /**
     * @param keycloakSub claim {@code sub} do token
     * @param nome        nome novo, já validado na borda (não vazio, até 150 caracteres); o caso de uso exige ao menos duas palavras
     */
    public record Comando(String keycloakSub, String nome) {
    }
}
