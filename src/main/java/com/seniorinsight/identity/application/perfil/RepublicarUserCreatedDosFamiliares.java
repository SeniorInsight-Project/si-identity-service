package com.seniorinsight.identity.application.perfil;

import java.util.List;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.evento.UserCreated;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Backfill do care-service: grava de novo na outbox um {@code user.created} para cada perfil FAMILIAR, para quem se
 * cadastrou antes de o care passar a consumir o evento. Os eventos têm id novo; o care evita duplicar o Familiar pelo
 * {@code keycloakSub}, então rodar mais de uma vez é seguro (só gera tráfego).
 *
 * <p>Percorre os perfis em lotes por id, uma transação por lote, para não segurar a tabela inteira de uma vez.
 */
@Service
public class RepublicarUserCreatedDosFamiliares {

    private static final Logger log = LoggerFactory.getLogger(RepublicarUserCreatedDosFamiliares.class);
    private static final UUID PRIMEIRO_ID = new UUID(0L, 0L);

    private final RepositorioPerfis repositorio;
    private final PublicadorEventos publicador;
    private final TransactionTemplate transacao;

    public RepublicarUserCreatedDosFamiliares(RepositorioPerfis repositorio, PublicadorEventos publicador,
                                              TransactionTemplate transacao) {
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.transacao = transacao;
    }

    /** @return quantos eventos foram gravados na outbox */
    public int executar(int tamanhoLote) {
        if (tamanhoLote < 1) {
            throw new IllegalArgumentException("tamanhoLote deve ser positivo");
        }
        int total = 0;
        UUID ultimo = PRIMEIRO_ID;
        while (true) {
            UUID depoisDe = ultimo;
            List<Perfil> lote = transacao.execute(status -> {
                List<Perfil> perfis = repositorio.listarPorTipoApos(TipoPerfil.FAMILIAR, depoisDe, tamanhoLote);
                perfis.forEach(perfil -> publicador.publicar(UserCreated.de(perfil)));
                return perfis;
            });
            if (lote == null || lote.isEmpty()) {
                break;
            }
            total += lote.size();
            ultimo = lote.getLast().id();
        }
        log.info("Republicação: {} eventos user.created de familiares gravados na outbox", total);
        return total;
    }
}
