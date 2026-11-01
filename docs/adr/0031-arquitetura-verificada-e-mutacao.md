# 0031 · Regras de camadas no ArchUnit e testes de mutação nas regras de negócio

**Contexto.** O doc 09 pede teste de arquitetura (ArchUnit e Modulith) e mutation testing (PIT) nas regras de negócio
críticas. O Spring Modulith já conferia as fronteiras entre módulos (ADR 0001), mas nada impedia um controller de
usar um repositório ou um service de montar SQL, desvios da regra de camadas (controller → service → repository). A
cobertura de linhas também não mostrava se os testes das regras de adesão realmente pegariam um erro de fronteira
(`<` no lugar de `<=`).

**Decisão.**
- **ArchUnit** (`ArquiteturaTest`, roda no `verify`): controller não depende de repositório; SQL (`JdbcClient`,
  `org.springframework.jdbc.core`, `java.sql.Connection`) só em repositório (e na configuração do contexto do banco);
  service não depende de controller; repositório não depende de service nem de controller; modelo não depende de
  nenhuma camada; `@RestController` só em `controller`; `@Repository` só em `repository` (exceção: a trava de tarefas
  de `comum`). O único desvio encontrado, o `MapaController` lendo constantes de zoom do repositório, passou a
  lê-las do service.
- **PIT** (perfil `mutacao`, roda no CI depois do `verify`): `Situacao`, `Adesao`, `Projecao` e `RegrasDeAlerta`,
  com os mutadores `STRONGER`, contra os testes de unidade dessas regras. Mínimo de 95% de mutantes mortos. A
  primeira medição deu 78%; os sobreviventes viraram testes de fronteira (limites de dias dos prazos, limite de
  alerta, janela de 6 meses, prazo no mês atual, ritmo zero ou negativo, projeção exatamente na meta). Hoje: 98%;
  os 4 restantes são mutantes equivalentes.

**Alternativas descartadas.** jMolecules/ArchUnit com estereótipos por anotação: as camadas já são pacotes com nome
fixo em todos os módulos. PIT no projeto inteiro: lento (Testcontainers) e com pouco valor fora das regras de cálculo.

**Consequências.** Uma regra nova de adesão, situação, projeção ou alerta precisa de testes que matem os mutantes,
senão o CI falha. Relatório em `backend/target/pit-reports/index.html`.
