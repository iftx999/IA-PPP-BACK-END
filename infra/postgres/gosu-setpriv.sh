#!/bin/sh
# Substitui o gosu da imagem base (binário Go com bibliotecas desatualizadas; Trivy aponta CVEs críticos) pelo
# setpriv do util-linux do Debian. Mesmo uso do entrypoint oficial: gosu USUARIO COMANDO [ARGS...]
set -e
usuario=$1; shift
exec setpriv --reuid="$usuario" --regid="$usuario" --init-groups -- "$@"
