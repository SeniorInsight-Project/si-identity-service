package com.seniorinsight.identity.adapters.in.web;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.identity.adapters.in.web.PerfilController.PerfilResponse;
import com.seniorinsight.identity.application.pareamento.GerarCodigoPareamento;
import com.seniorinsight.identity.application.pareamento.ResgatarCodigoPareamento;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade.Tokens;
import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;
import com.seniorinsight.identity.domain.pareamento.Plataforma;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pareamento do aparelho do idoso por código ou QR (HU-007), rotas do contrato {@code openapi/identity-v1.yaml}. */
@RestController
@Tag(name = "Pareamento")
public class PareamentoController {

    static final String FORMATO_CODIGO = "^[A-Z0-9]{6,8}$";

    private final GerarCodigoPareamento gerarCodigo;
    private final ResgatarCodigoPareamento resgatarCodigo;

    public PareamentoController(GerarCodigoPareamento gerarCodigo, ResgatarCodigoPareamento resgatarCodigo) {
        this.gerarCodigo = gerarCodigo;
        this.resgatarCodigo = resgatarCodigo;
    }

    @PostMapping("/v1/pareamentos/codigos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "gerarCodigoPareamento", summary = "Gera um código de pareamento para o aparelho do idoso",
            description = "Exige a role familiar. Com idosoId, só o Familiar Administrador da rede do idoso (confirmado "
                    + "no care-service) pode gerar. O código é de uso único; gerar outro para o mesmo idoso invalida "
                    + "o anterior.")
    @ApiResponse(responseCode = "201", description = "Código gerado")
    @ApiResponse(responseCode = "400", description = "Dados da requisição inválidos",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sem a role familiar ou sem ser Administrador da rede do idoso",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    public CodigoPareamentoResponse gerar(@Valid @RequestBody GerarCodigoPareamentoRequest requisicao,
                                          @Parameter(hidden = true) JwtAuthenticationToken autenticacao) {
        CodigoPareamento codigo = gerarCodigo.executar(new GerarCodigoPareamento.Comando(
                autenticacao.getToken().getSubject(),
                autenticacao.getToken().getTokenValue(),
                requisicao.nomeIdoso(),
                requisicao.idosoId()));
        return new CodigoPareamentoResponse(codigo.codigo(), codigo.qrCodeConteudo(), codigo.expiraEm());
    }

    @PostMapping("/v1/pareamentos/resgates")
    @SecurityRequirements
    @Operation(operationId = "resgatarCodigoPareamento",
            summary = "Resgata o código no aparelho do idoso e devolve os tokens da sessão",
            description = "Rota pública, chamada pelo app do idoso. Cria (ou reaproveita) o usuário e o perfil do idoso, "
                    + "registra a sessão do aparelho e faz o token exchange no Keycloak.")
    @ApiResponse(responseCode = "200", description = "Aparelho pareado")
    @ApiResponse(responseCode = "400", description = "Dados da requisição inválidos",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "404", description = "Código não existe",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "409", description = "Código já resgatado",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "410", description = "Código expirado",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    public ResultadoPareamentoResponse resgatar(@Valid @RequestBody ResgatarCodigoPareamentoRequest requisicao) {
        ResgatarCodigoPareamento.Resultado resultado = resgatarCodigo.executar(new ResgatarCodigoPareamento.Comando(
                requisicao.codigo(), requisicao.identificadorAparelho(), requisicao.plataforma()));
        return new ResultadoPareamentoResponse(PerfilResponse.de(resultado.perfil()), resultado.sessaoAparelhoId(),
                resultado.idosoId(), TokensResponse.de(resultado.tokens()));
    }

    @Schema(name = "GerarCodigoPareamentoRequest")
    public record GerarCodigoPareamentoRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 150,
                    description = "Nome do perfil do idoso que será criado no resgate")
            @NotBlank @Size(max = 150) String nomeIdoso,
            @Schema(nullable = true, description = "Idoso já cadastrado no care-service, quando houver")
            UUID idosoId) {
    }

    @Schema(name = "CodigoPareamento")
    public record CodigoPareamentoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = FORMATO_CODIGO, example = "K7P2QX")
            String codigo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "seniorinsight://pareamento?codigo=K7P2QX") String qrCodeConteudo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiraEm) {
    }

    @Schema(name = "ResgatarCodigoPareamentoRequest")
    public record ResgatarCodigoPareamentoRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = FORMATO_CODIGO, example = "K7P2QX")
            @NotNull @Pattern(regexp = FORMATO_CODIGO) String codigo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255)
            @NotBlank @Size(max = 255) String identificadorAparelho,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Plataforma plataforma) {
    }

    @Schema(name = "ResultadoPareamento")
    public record ResultadoPareamentoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PerfilResponse perfil,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID sessaoAparelhoId,
            @Schema(nullable = true, description = "Id do idoso no care-service, o mesmo informado ao gerar o código. "
                    + "O app do idoso usa esse id nas rotas por idoso (consentimentos, exclusão); null quando o código "
                    + "foi gerado sem idosoId.")
            UUID idosoId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TokensResponse tokens) {
    }

    @Schema(name = "Tokens")
    public record TokensResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Validade do access token, em segundos")
            long expiresIn,
            @Schema(description = "Validade do refresh token, em segundos") Long refreshExpiresIn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "Bearer") String tokenType) {

        static TokensResponse de(Tokens tokens) {
            return new TokensResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(),
                    tokens.refreshExpiresIn(), "Bearer");
        }
    }
}
