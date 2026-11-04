package com.seniorinsight.identity.adapters.out.care;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.seniorinsight.identity.application.pareamento.RedeIndisponivel;
import com.seniorinsight.identity.application.porta.saida.RedeDoIdoso;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Confirma no care-service, por REST interno (sem passar pelo gateway), se o familiar é Administrador da rede do idoso
 * (RF-007). Usa {@code GET /api/v1/idosos/{idosoId}/rede} do contrato {@code care-v1.yaml} com o token do próprio
 * familiar, então o care-service aplica as regras de acesso dele.
 *
 * <p>Um membro é o familiar quando {@code usuarioId} é o {@code sub} do token, e é Administrador quando o
 * {@code papelAcesso} está em {@code identity.care-service.papeis-administrador}. 403 e 404 do care-service viram
 * "sem vínculo"; erro de rede ou 5xx vira {@link RedeIndisponivel}.
 */
@Component
class RedeDoIdosoCareService implements RedeDoIdoso {

    private final RestClient rest;
    private final Set<String> papeisAdministrador;

    RedeDoIdosoCareService(RestClient.Builder builder, PropriedadesCareService propriedades) {
        this.rest = builder.baseUrl(propriedades.url().toString()).build();
        this.papeisAdministrador = propriedades.papeisAdministrador().stream()
                .map(papel -> papel.strip().toUpperCase())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean familiarEhAdministrador(UUID idosoId, String familiarSub, String tokenFamiliar) {
        List<MembroRede> membros;
        try {
            membros = rest.get()
                    .uri("/api/v1/idosos/{idosoId}/rede", idosoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFamiliar)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (HttpClientErrorException e) {
            if (semVinculo(e.getStatusCode())) {
                return false;
            }
            throw new RedeIndisponivel(e);
        } catch (RestClientException e) {
            throw new RedeIndisponivel(e);
        }
        return membros != null && membros.stream().anyMatch(membro ->
                familiarSub.equals(membro.usuarioId())
                        && membro.papelAcesso() != null
                        && papeisAdministrador.contains(membro.papelAcesso().strip().toUpperCase()));
    }

    private static boolean semVinculo(HttpStatusCode status) {
        return status.isSameCodeAs(HttpStatus.FORBIDDEN) || status.isSameCodeAs(HttpStatus.NOT_FOUND);
    }

    /** Campos do schema {@code MembroRede} que interessam aqui. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MembroRede(String usuarioId, String papelAcesso) {
    }
}
