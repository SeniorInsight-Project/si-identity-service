package com.seniorinsight.identity.adapters.in.job;

import com.seniorinsight.identity.application.perfil.RepublicarUserCreatedDosFamiliares;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Roda a republicação dos {@code user.created} de familiares ao subir o serviço, só quando
 * {@code IDENTITY_REPUBLICAR_FAMILIARES=true}. Uso: subir uma vez com a variável ligada e desligá-la em seguida.
 */
@Component
@ConditionalOnProperty(name = "identity.republicacao.familiares.habilitada", havingValue = "true")
class RepublicacaoFamiliaresRunner implements ApplicationRunner {

    private final RepublicarUserCreatedDosFamiliares republicar;
    private final int tamanhoLote;

    RepublicacaoFamiliaresRunner(RepublicarUserCreatedDosFamiliares republicar,
                                 @Value("${identity.republicacao.familiares.tamanho-lote:200}") int tamanhoLote) {
        this.republicar = republicar;
        this.tamanhoLote = tamanhoLote;
    }

    @Override
    public void run(ApplicationArguments args) {
        republicar.executar(tamanhoLote);
    }
}
