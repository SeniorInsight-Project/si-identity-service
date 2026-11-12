package com.seniorinsight.identity.adapters.out.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.springframework.data.domain.Pageable;
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
                perfil.email(), perfil.idosoId(), perfil.criadoEm(), perfil.atualizadoEm()) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void atualizarNome(Perfil perfil) {
        jpa.atualizarNome(perfil.id(), perfil.nome(), perfil.atualizadoEm());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Perfil> listarPorTipoApos(TipoPerfil tipo, UUID depoisDe, int limite) {
        return jpa.findByTipoAndIdGreaterThanOrderByIdAsc(tipo, depoisDe, Pageable.ofSize(limite)).stream()
                .map(PerfilEntidade::paraDominio)
                .toList();
    }
}
