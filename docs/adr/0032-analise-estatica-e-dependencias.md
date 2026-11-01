# 0032 · Análise estática, varredura de dependências e CodeQL no CI

**Contexto.** O doc 09 lista SpotBugs/Error Prone, OWASP Dependency-Check, CodeQL e Trivy. O CI tinha só o Trivy nas
imagens publicadas e o Gitleaks; vulnerabilidade numa dependência só aparecia ao publicar imagem, e não havia análise
estática do código.

**Decisão.**
- **SpotBugs + FindSecBugs** (perfil `analise`, `spotbugs:check` no CI): esforço máximo, achados médios e altos
  bloqueiam. A primeira rodada deu 165 achados. Corrigidos: JSON de erro montado com `String.format` em três filtros
  (agora `RespostaDeProblema`, serializado pelo Jackson); pergunta do assistente capaz de fechar a tag `<pergunta>`
  (agora `<` e `>` viram ‹ e ›); `catch (NullPointerException)`; array estático mutável (`Elo.HASH_INICIAL`); nome de
  arquivo temporário com dado do path; operador da plataforma sem `sub` (agora 401). O restante foi revisado e
  excluído com o motivo em `backend/spotbugs-excluir.xml` (ex.: `EI_EXPOSE_REP` em records, accessor de record
  chamado duas vezes, CSRF em API com token bearer).
- **Dependências com o Trivy** (`fs` no `pom.xml`) em vez do OWASP Dependency-Check: o mesmo scanner e a mesma base
  das imagens, sem chave da NVD nem download de horas. Crítica com correção bloqueia; altas vão para a aba Security
  (SARIF). O front segue com `npm audit --audit-level=high`.
- **CodeQL** (`security-extended`) no back-end (Java, sem build) e no front-end (TypeScript), em cada PR, no `main` e
  toda segunda-feira.
- Error Prone ficou de fora: exige flags de `--add-exports` no `javac` a cada versão do JDK e se sobrepõe ao SpotBugs e
  ao CodeQL, que já rodam.

**Consequências.** Uma dependência com vulnerabilidade crítica corrigível bloqueia o PR antes da imagem. Exclusão nova
no SpotBugs precisa de motivo no XML e passa pela revisão do PR.
