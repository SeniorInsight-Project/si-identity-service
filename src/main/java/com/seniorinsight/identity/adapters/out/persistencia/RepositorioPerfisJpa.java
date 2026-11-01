package com.seniorinsight.identity.adapters.out.persistencia;

import java.util.Optional;

import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class RepositorioPerfisJpa implements RepositorioPerfis {

    private final PerfilJpaRepository jpa;

    RepositorioPerfisJpa(PerfilJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Perfil> buscarPorKeycloakSub(String keycloakSub) {
        return jpa.findByKeycloakSub(keycloakSub).map(PerfilEntidade::paraDominio);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean inserirSeAusente(Perfil perfil) {
        return jpa.inserirSeAusente(perfil.id(), perfil.keycloakSub(), perfil.tipo().name(), perfil.nome(),
                perfil.email(), perfil.criadoEm(), perfil.atualizadoEm()) == 1;
    }
}
