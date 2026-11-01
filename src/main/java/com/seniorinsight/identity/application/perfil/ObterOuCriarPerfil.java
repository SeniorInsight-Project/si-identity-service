package com.seniorinsight.identity.application.perfil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.evento.UserCreated;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil do usuário autenticado. No primeiro acesso de quem tem o papel {@code familiar} (dado pelo Keycloak a quem
 * se cadastra), cria o perfil FAMILIAR e publica {@code user.created} na mesma transação. O perfil IDOSO nasce no
 * pareamento (HU-007), não aqui.
 */
@Service
public class ObterOuCriarPerfil {

    static final String PAPEL_FAMILIAR = "familiar";

    private final RepositorioPerfis repositorio;
    private final PublicadorEventos publicador;

    public ObterOuCriarPerfil(RepositorioPerfis repositorio, PublicadorEventos publicador) {
        this.repositorio = repositorio;
        this.publicador = publicador;
    }

    @Transactional
    public Perfil executar(Comando comando) {
        return repositorio.buscarPorKeycloakSub(comando.keycloakSub())
                .orElseGet(() -> criar(comando));
    }

    private Perfil criar(Comando comando) {
        if (!comando.papeis().contains(PAPEL_FAMILIAR)) {
            throw new PerfilNaoEncontrado(comando.keycloakSub());
        }
        // Microssegundos: a precisão do TIMESTAMPTZ, para o perfil devolvido ser igual ao relido do banco depois.
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Perfil novo = Perfil.novoFamiliar(comando.keycloakSub(), comando.nomeExibicao(), comando.email(), agora);
        if (repositorio.inserirSeAusente(novo)) {
            publicador.publicar(UserCreated.de(novo));
            return novo;
        }
        // Corrida no primeiro acesso: outra requisição criou o perfil (e publicou o evento) antes desta.
        return repositorio.buscarPorKeycloakSub(comando.keycloakSub())
                .orElseThrow(() -> new IllegalStateException(
                        "Perfil de " + comando.keycloakSub() + " não foi inserido nem encontrado"));
    }

    /**
     * Dados do token do usuário.
     *
     * @param papeis      roles de realm
     * @param nome        claim {@code name}
     * @param nomeUsuario claim {@code preferred_username}, usado quando não há {@code name}
     * @param email       claim {@code email}
     */
    public record Comando(String keycloakSub, Set<String> papeis, String nome, String nomeUsuario, String email) {

        public Comando {
            papeis = papeis == null ? Set.of() : Set.copyOf(papeis);
        }

        String nomeExibicao() {
            if (nome != null && !nome.isBlank()) {
                return nome;
            }
            if (nomeUsuario != null && !nomeUsuario.isBlank()) {
                return nomeUsuario;
            }
            return keycloakSub;
        }
    }
}
