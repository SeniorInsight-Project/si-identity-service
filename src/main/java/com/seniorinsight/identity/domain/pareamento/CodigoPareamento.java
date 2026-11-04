package com.seniorinsight.identity.domain.pareamento;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Código de uso único que o Familiar Administrador gera para parear o aparelho do idoso (HU-007).
 *
 * @param codigo      6 caracteres de {@link #ALFABETO}, sempre no formato {@code ^[A-Z0-9]{6,8}$} do contrato
 * @param familiarSub claim {@code sub} de quem gerou
 * @param nomeIdoso   nome do perfil do idoso que nasce no resgate
 * @param idosoId     idoso já cadastrado no care-service, ou {@code null}
 * @param expiraEm    fim da validade
 */
public record CodigoPareamento(String codigo, String familiarSub, String nomeIdoso, UUID idosoId, Instant expiraEm) {

    /** Sem 0/O e 1/I/L, que se confundem quando o código é lido em voz alta ou digitado pelo idoso. */
    static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int TAMANHO = 6;
    static final String ESQUEMA_QR = "seniorinsight://pareamento?codigo=";

    private static final Pattern FORMATO = Pattern.compile("^[A-Z0-9]{6,8}$");
    private static final RandomGenerator ALEATORIO = new SecureRandom();

    public CodigoPareamento {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(familiarSub, "familiarSub");
        Objects.requireNonNull(nomeIdoso, "nomeIdoso");
        Objects.requireNonNull(expiraEm, "expiraEm");
        if (!formatoValido(codigo)) {
            throw new IllegalArgumentException("Código fora do formato: " + codigo);
        }
    }

    public static boolean formatoValido(String codigo) {
        return codigo != null && FORMATO.matcher(codigo).matches();
    }

    /** Sorteia um código novo (gerador criptográfico). */
    public static String sortear() {
        return sortear(ALEATORIO);
    }

    static String sortear(RandomGenerator aleatorio) {
        StringBuilder codigo = new StringBuilder(TAMANHO);
        for (int i = 0; i < TAMANHO; i++) {
            codigo.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /** Conteúdo do QR exibido no app do familiar. */
    public String qrCodeConteudo() {
        return ESQUEMA_QR + codigo;
    }

    public boolean expirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }
}
