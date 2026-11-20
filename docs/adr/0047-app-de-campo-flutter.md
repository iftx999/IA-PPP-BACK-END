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

- **Fila e sincronização (6c):** cada ação vira um evento na tabela `evento` do mesmo banco **antes** de mudar a
  tela (id e sequência por OS únicos; a sequência continua a `ultimaSequencia` que o pacote traz por OS). Envio em
  lotes de até 200, na ordem de registro, um envio por vez, com novas tentativas em 5 s, 15 s, 30 s, 1, 2 e 5 min, ao
  voltar para o app e em "Enviar agora". Qualquer resposta tira o evento da fila (o servidor guarda até o que
  aguarda o anterior); lote que o servidor não lê é reenviado um a um. A tela mostra o pacote do servidor com as
  ações do celular por cima até o pacote seguinte refleti-las; recusadas e superadas voltam e viram aviso. Celular
  revogado: a fila é enviada antes de apagar e o app confirma a limpeza (`/dispositivo/apagado`). Encerrar a jornada
  exige a fila vazia e apaga o pacote do celular (10.9.6). O envio com o app fechado (WorkManager ou serviço em
  primeiro plano) entra com as posições no 6e.

- **Execução e fotos (6d):** tela da OS com iniciar, paralisar, retomar, devolver e encerrar; motivo obrigatório
  (com observação quando o motivo exige) e encerramento com o formulário dos parâmetros da versão, conferido no
  celular com as mesmas regras do servidor (`ValoresDoEncerramento`: obrigatório, faixa, inteiro, opções, condição,
  quantidade de fotos), para o agente corrigir na hora mesmo sem sinal. Fotos pela câmera com `image_picker` (BSD e
  Apache 2.0), já reduzidas a 1.600 px em JPEG 80%, na pasta privada do app; entram na fila na mesma transação do
  encerramento e vão depois dos eventos, em partes de 1 MB com SHA-256, retomando pelo `GET /anexos/{id}`. Enviada
  ou recusada, a foto sai do celular; a de encerramento recusado nem é enviada.

- **Posições e envio em segundo plano (6e):** a coleta liga quando a jornada abre (inclusive a iniciada sem sinal e
  a reaberta depois de o Android matar o app) e desliga quando ela fecha ou a sessão acaba. Usa o serviço em primeiro
  plano do `geolocator_android` (já no projeto): notificação fixa "Jornada em andamento" enquanto coleta (RN-27: o
  agente vê que está sendo localizado), sem `ACCESS_BACKGROUND_LOCATION` (o serviço sempre começa com o app aberto).
  O mesmo serviço mantém o processo vivo com o app em segundo plano, então a fila continua saindo pelas novas
  tentativas do 6c. **WorkManager não foi adotado:** fora da jornada nada é coletado, o fim da jornada exige a fila
  vazia, e um envio sem o app exigiria renovar o token OIDC num isolate sem tela. Leituras a cada 30 s; o app guarda
  uma a cada 2 min **ou** 100 m andados (5 min ou 250 m com bateria até 20% fora da tomada, leitura a cada 1 min e
  precisão média), descartando leituras com precisão pior que 150 m. As posições ficam numa tabela própria do
  SQLCipher (versão 4 do banco) e vão depois dos eventos e das fotos, em lotes de até 500; uma leitura nova dispara o
  envio a partir de 5 na fila, a não ser que o app esteja esperando o sinal voltar. Não contam como ações pendentes
  nem prendem o fim da jornada (o servidor aceita as atrasadas da última jornada, dentro do horário dela); esperam
  enquanto o início feito sem sinal não chegou ao servidor; 409 (nenhuma jornada) e 400/422 descartam o lote;
  passadas 7 dias, saem do celular sem envio. Bateria e permissão de notificação (Android 13+) por um canal nativo
  pequeno em `MainActivity.kt`: o `battery_plus` (BSD) traz `upower` e `dbus` (MPL 2.0) para o Linux. Tela "Fila de
  envio" (pelo indicador da fila ou pelo menu): pendentes, último envio, próxima tentativa, situação da localização
  (com "Abrir ajustes" quando a permissão foi retirada) e as ações dos últimos 2 dias com a resposta do servidor.

**Consequências.** Mais um repositório e uma cadeia de build (Flutter e Android SDK) para manter. A publicação
interna (assinatura e distribuição pelo MDM da empresa) entra no fim do M6. Próximas etapas: jornada e pacote do dia
(6b), fila local criptografada com SQLCipher e sincronização (6c), encerramento com parâmetros e fotos (6d), posições e
tela da fila (6e). Falta no M6: mapa da rota no app e publicação interna.
