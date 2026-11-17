#!/bin/sh
case "$1" in
  *sername*) echo "x-access-token" ;;
  *) exec bun /home/z/skip-ad-repo/scripts/print-token.ts ;;
esac
