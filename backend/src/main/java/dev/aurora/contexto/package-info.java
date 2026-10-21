/**
 * Contexto de acesso da requisição (tenant, usuário, permissões, escopo). Módulo base:
 * não depende de nenhum outro, e todos os módulos podem usá-lo sem criar ciclos.
 */
@NullMarked
package dev.aurora.contexto;

import org.jspecify.annotations.NullMarked;
