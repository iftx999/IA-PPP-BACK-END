"""Gera realm-aurora-producao.json a partir do realm de desenvolvimento (ADR 0022).

Produção não herda usuários nem segredos de desenvolvimento: endereços, segredo do cliente de administração e SMTP
viram placeholders ${VAR} (o Keycloak os substitui pelas variáveis de ambiente na importação). Política de senha e
proteção contra força bruta mais rígidas. Uso: python3 infra/keycloak/gerar-realm-producao.py
"""
import json
from pathlib import Path

PASTA = Path(__file__).resolve().parent


def main() -> None:
    realm = json.loads((PASTA / "realm-aurora.json").read_text(encoding="utf-8"))
    realm.pop("users", None)
    realm.update({
        "sslRequired": "external",
        "passwordPolicy": "length(12) and notUsername and notEmail and passwordHistory(5) and maxLength(128)",
        "bruteForceProtected": True,
        "permanentLockout": False,
        "failureFactor": 5,
        "waitIncrementSeconds": 60,
        "maxFailureWaitSeconds": 900,
        "maxDeltaTimeSeconds": 43200,
        "quickLoginCheckMilliSeconds": 1000,
        "minimumQuickLoginWaitSeconds": 60,
        # Sessão: 30 min ociosa, 10 h no máximo (um dia de trabalho).
        "ssoSessionIdleTimeout": 1800,
        "ssoSessionMaxLifespan": 36000,
        # TOTP disponível para quem quiser (H9); obrigatoriedade por perfil fica para a política de MFA.
        "otpPolicyType": "totp",
        "otpPolicyAlgorithm": "HmacSHA256",
        "otpPolicyDigits": 6,
        "otpPolicyPeriod": 30,
        "smtpServer": {
            "host": "${AURORA_SMTP_HOST}",
            "port": "${AURORA_SMTP_PORTA}",
            "from": "${AURORA_SMTP_REMETENTE}",
            "fromDisplayName": "Aurora",
            "auth": "true",
            "user": "${AURORA_SMTP_USUARIO}",
            "password": "${AURORA_SMTP_SENHA}",
            "starttls": "true",
            "ssl": "false",
        },
    })
    for cliente in realm.get("clients", []):
        if cliente.get("clientId") == "aurora-web":
            cliente["redirectUris"] = ["${AURORA_URL_FRONT}/*"]
            cliente["webOrigins"] = ["${AURORA_URL_FRONT}"]
            cliente["attributes"] = {**cliente.get("attributes", {}),
                                     "post.logout.redirect.uris": "${AURORA_URL_FRONT}/*"}
        if cliente.get("clientId") == "aurora-api-admin":
            cliente["secret"] = "${AURORA_IDENTIDADE_SEGREDO}"
    destino = PASTA / "realm-aurora-producao.json"
    destino.write_text(json.dumps(realm, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(destino)


if __name__ == "__main__":
    main()
