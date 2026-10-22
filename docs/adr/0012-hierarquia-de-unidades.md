# 0012 · Hierarquia de unidades e caminho ltree

**Contexto.** O cadastro de unidades (H1) cria, edita, move e inativa nós da árvore Grupo → Concessão →
Município → Setor/ETE/EEE. Escopo de acesso, RLS e herança de metas dependem do caminho `ltree` (ADR 0002).

**Decisão.**
- A hierarquia é fixa no domínio (`TipoUnidade.tiposPai`) e exposta em `GET /api/v1/unidades/tipos`, para o
  front não duplicar a regra. O grupo é a raiz única do tenant: nasce no onboarding e não é criado, movido
  nem inativado pela administração.
- O caminho é feito dos IDs (`u` + UUID sem hífens), nunca do nome ou código: renomear não toca no caminho.
- Mover reescreve o caminho de toda a subárvore em uma instrução (`novoPai.caminho || subpath(...)`), com a
  subárvore travada (`FOR UPDATE`). Ciclos e pais de tipo inválido são recusados. Escopos e metas seguem o
  novo pai automaticamente, pois referenciam o ID.
- Inativar exige não haver filhas ativas nem concessões de acesso vigentes na unidade; o histórico
  (medições, metas, imóveis) é preservado. Reativar exige o pai ativo.
- Toda operação vale dentro do escopo de quem a faz, inclusive o pai de destino (permite delegar a gestão de
  um município sem expor o resto do tenant). Permissão `unidade:gerenciar`.

**Consequências.** Mover uma unidade grande reescreve muitas linhas numa transação (aceitável para a
cardinalidade esperada, de milhares de nós). Importação de geometria fica para o Marco 5 (GIS).
