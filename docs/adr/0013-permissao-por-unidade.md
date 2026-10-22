# 0013 · Permissão vale só na unidade onde foi concedida

**Contexto.** O banco concede perfil por unidade (`usuario_perfil_escopo`), mas a API somava as permissões de
todas as concessões e as aplicava em todo o escopo. Quem tinha "Leitura" na Concessão A e "Gestor" na Cidade B
podia editar metas da concessão inteira (viola RN-13: perfil define o quê, escopo define onde).

**Decisão.**
- O `ContextoAcesso` guarda, para cada permissão, as unidades (caminhos ltree) onde ela vale. O escopo efetivo
  começa como a união e é **restringido pela permissão que autoriza a operação**.
- Endpoints declaram a permissão com `@ExigePermissao("x")` (substitui `@PreAuthorize`). Um interceptador
  recusa com 403 quem não a tem em lugar nenhum e troca o escopo da requisição por `escoposDe(x)` antes de
  qualquer acesso ao banco. Como o escopo vai para `app_escopos()`, repositórios e RLS continuam iguais e
  passam a respeitar a regra automaticamente.
- Checagens dentro da operação usam `podeEm(permissao, caminho)`. Ex.: matrícula sem máscara só se
  `dados_pessoais:ler` vale na unidade consultada (todas as linhas estão dentro dela).
- Gestão de perfis e usuários continua exigindo `usuario:gerenciar` concedida na unidade raiz.

**Consequências.** Uma operação usa uma permissão principal; quem precisar combinar duas (ex.: exportar exige
`relatorio:exportar`, e as linhas são as do escopo dessa permissão) restringe pela principal e confere as
demais com `podeEm`. O `/eu` continua devolvendo a união das permissões, para o front montar o menu.
