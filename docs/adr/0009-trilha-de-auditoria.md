# 0009 · Trilha de auditoria encadeada por HMAC

**Contexto.** A auditoria precisa ser somente leitura e protegida contra edição (doc 08, 8.7), inclusive contra
quem tem acesso direto ao banco. A versão anterior encadeava os eventos por hash (`hash_anterior` → `hash`), mas o
algoritmo exato se perdeu com o código: nenhuma combinação simples de campos com SHA-256 reproduz os hashes gravados,
o que indica HMAC com chave.

**Decisão.**
- **Formato 2:** `hash = HMAC-SHA256(chave, hash_anterior || JSON canônico)`, onde o JSON tem chaves em ordem
  alfabética (inclusive em `detalhes`), `ocorridoEm` em ISO-8601 UTC com microssegundos e o IP na forma canônica
  do PostgreSQL.
- **Chave:** vem de `AURORA_AUDITORIA_CHAVE` (cofre de segredos), nunca do banco.
- **Somente inserção:** a aplicação (`aurora_app`) só tem SELECT e INSERT em `evento_auditoria`.
- **Gravação serializada:** cada tenant tem seu último elo em `cadeia_auditoria`, travado com `FOR UPDATE`, e o
  registro participa da transação da ação auditada.
- **Eventos importados:** ficam com `formato_hash` nulo (legado) e só têm o elo conferido; o primeiro evento novo
  encadeia no último hash legado.

**Consequências.** Alterar, remover ou reordenar eventos quebra a verificação (`AuditoriaService.verificar`),
coberta por teste que adultera o banco. Trocar a chave exige iniciar nova cadeia (planejar rotação com versão de
chave). Volume alto de eventos por tenant serializa nas gravações; se virar gargalo, a alternativa é encadear por
lote.
