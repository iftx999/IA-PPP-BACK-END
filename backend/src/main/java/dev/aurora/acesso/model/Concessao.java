package dev.aurora.acesso.model;

/** Uma permissão concedida ao usuário em uma unidade (caminho ltree); vale também nas descendentes. */
public record Concessao(String permissao, String caminhoUnidade) {
}
