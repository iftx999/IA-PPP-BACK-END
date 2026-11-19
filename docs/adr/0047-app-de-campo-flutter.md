# 0047 · App de campo em Flutter: projeto próprio, login OIDC com PKCE, PIN e sessão sem rede

**Contexto.** O sexto marco dos serviços de campo (doc 10, M6; J8 a J10 e J13) é o app Android que as equipes usam
nos celulares da empresa (P10), inclusive sem sinal. A API já existe (ADR 0045, ADR 0046); o protótipo de 8 telas foi
aceito como base (19/11/2026).

**Decisão.**
- **Projeto próprio** (`aurora-app-campo`, repositório separado), Flutter estável 3.47.9, só Android por enquanto (o
  código já nasce compatível com iOS). Estado com Riverpod, rotas com go_router; dependências só MIT, BSD ou Apache
  2.0, conferidas no pub.dev antes de entrar (como no front, ADR 0010).
- **Login:** Authorization Code + PKCE no navegador do sistema (flutter_appauth) contra o realm da empresa
  (`<identidade>/realms/<código>`, ADR 0027). Novo cliente público **`aurora-campo`** em todos os realms (dev,
  produção e o modelo dos clientes novos), com retorno `dev.aurora.campo:/oauthredirect`, audiência `aurora-api` e
  escopo opcional `offline_access`: o token de renovação offline permite enviar a fila quando o sinal volta, sem novo
  login.
- **Sessão sem rede por até 7 dias** (10.9.6) contados do último contato com o Keycloak (login ou renovação); depois,
  login de novo com sinal.
- **PIN de 6 números** para abrir o app, guardado só como PBKDF2-HMAC-SHA256 (120 mil iterações, sal aleatório) no
  Keystore (flutter_secure_storage); sequências e repetidos recusados; 5 erros seguidos apagam a sessão e o PIN.
  Biometria (local_auth) como alternativa.
- **Celular:** identificador gerado no app (UUID) e cadastrado em `POST /api/v1/campo/dispositivo` depois do login;
  revogado (`DISPOSITIVO_REVOGADO`) apaga tokens, PIN e dados locais, mantendo só o identificador.
- **Configuração na compilação** (`--dart-define`: `AURORA_API`, `AURORA_IDENTIDADE`); HTTP só no build de
  desenvolvimento (config de segurança de rede de debug para 10.0.2.2); produção só HTTPS com autoridades do sistema;
  `allowBackup=false`.
- **Tema:** Material 3 com os tokens da Aurora, claro e escuro seguindo o celular; telas de entrada no escuro da marca;
  alvos de toque de 56 dp e texto maior (sol e luva). Testes de widget com as diretrizes de acessibilidade do Android
  (alvo, rótulo e contraste).

- **GPS e navegação (6b):** `geolocator_android` e `geolocator_platform_interface` (MIT) em vez do pacote geral
  `geolocator`, que puxa `dbus`, `geoclue` e `gsettings` (MPL 2.0) para o Linux; `url_launcher` (BSD) abre o app de
  mapa escolhido no celular com um link `geo:`. O último pacote baixado e o início de jornada feito sem sinal ficam no
  banco SQLCipher do celular (chave aleatória de 256 bits no Keystore), apagado na revogação e na troca de empresa.

**Consequências.** Mais um repositório e uma cadeia de build (Flutter e Android SDK) para manter. A publicação
interna (assinatura e distribuição pelo MDM da empresa) entra no fim do M6. Próximas etapas: jornada e pacote do dia
(6b), fila local criptografada com SQLCipher e sincronização (6c), encerramento com parâmetros e fotos (6d), posições e
tela da fila (6e).
