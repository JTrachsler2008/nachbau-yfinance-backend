#!/usr/bin/env bash
# Smoke-Test gegen die laufende Anwendung (http://localhost:8080).
#
# Voraussetzungen:
#   1. Postgres laeuft:   podman machine start podman-machine-default && docker start portfolio-postgres
#   2. Anwendung laeuft:  IntelliJ (Application) oder ./mvnw spring-boot:run
#   3. Test-User existiert (einmalig anlegen, siehe --seed unten)
#
# Aufruf:
#   ./smoke-test.sh          nur testen
#   ./smoke-test.sh --seed   Test-User 'joel' / 'test1234' vorher anlegen (idempotent)

BASE_URL="http://localhost:8080"
USER="joel"
PASS="test1234"

pass=0
fail=0

# Prueft, ob ein Endpunkt den erwarteten HTTP-Status liefert.
check() {
  local name="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "  OK   $name (HTTP $actual)"
    pass=$((pass + 1))
  else
    echo "  FAIL $name (erwartet HTTP $expected, war HTTP $actual)"
    fail=$((fail + 1))
  fi
}

if [ "$1" = "--seed" ]; then
  echo "Lege Test-User '$USER' an (Passwort '$PASS', BCrypt via pgcrypto)..."
  docker exec portfolio-postgres psql -U portfolio -d portfolio -q \
    -c "CREATE EXTENSION IF NOT EXISTS pgcrypto;" \
    -c "INSERT INTO users (username, email, password_hash)
        VALUES ('$USER', '$USER.trachsler@allianz.com', crypt('$PASS', gen_salt('bf', 10)))
        ON CONFLICT (username) DO NOTHING;"
  echo ""
fi

echo "=== Grundgeruest: YOUNGOITV-409 bis -417 ==="

# YOUNGOITV-409: Anwendung startet, Actuator-Health oeffentlich erreichbar
check "Health-Endpunkt oeffentlich" 200 \
  "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health")"

# YOUNGOITV-414: Auth-Filter blockt Zugriff ohne Token
check "Geschuetzter Endpunkt ohne Token -> 401" 401 \
  "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/users/me")"

# YOUNGOITV-416: Validierungsfehler mit stabiler JSON-Struktur
validation_body=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" -d '{"username":"","password":""}')
check "Login mit leeren Feldern -> 400" 400 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" -d '{"username":"","password":""}')"
case "$validation_body" in
  *fieldErrors*username*|*username*fieldErrors*)
    echo "  OK   Fehlerstruktur enthaelt fieldErrors"; pass=$((pass + 1)) ;;
  *)
    echo "  FAIL Fehlerstruktur ohne fieldErrors: $validation_body"; fail=$((fail + 1)) ;;
esac

# YOUNGOITV-412/413: Login prueft BCrypt-Hash, falsche Daten -> 401
check "Login mit falschem Passwort -> 401" 401 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" -d "{\"username\":\"$USER\",\"password\":\"falsch\"}")"

check "Login mit unbekanntem User -> 401" 401 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" -d '{"username":"gibtsnicht","password":"egal"}')"

# YOUNGOITV-412: Login mit korrekten Daten liefert Token
token=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

if [ -n "$token" ] && [ "${#token}" -gt 50 ]; then
  echo "  OK   Login liefert JWT (${#token} Zeichen)"
  pass=$((pass + 1))
else
  echo "  FAIL Login liefert keinen Token. Ist der Test-User angelegt? (./smoke-test.sh --seed)"
  fail=$((fail + 1))
  echo ""
  echo "Ergebnis: $pass OK, $fail FAIL"
  exit 1
fi

# YOUNGOITV-414: gueltiger Token laesst durch, manipulierter nicht
check "Geschuetzter Endpunkt mit gueltigem Token -> 200" 200 \
  "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/users/me" -H "Authorization: Bearer $token")"

check "Manipulierter Token -> 401" 401 \
  "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/users/me" -H "Authorization: Bearer ${token}xxx")"

check "Muell-Token -> 401" 401 \
  "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/users/me" -H "Authorization: Bearer not-a-real-token")"

# SEC: Fehlerantworten duerfen keine Exception-Details preisgeben
leak_body=$(curl -s "$BASE_URL/users/me" -H "Authorization: Bearer not-a-real-token")
case "$leak_body" in
  *Exception*|*"at ch.allianz"*)
    echo "  FAIL Fehlerantwort leakt Exception-Details"; fail=$((fail + 1)) ;;
  *)
    echo "  OK   Fehlerantwort leakt keine Exception-Details"; pass=$((pass + 1)) ;;
esac

# Der eingeloggte User wird korrekt aus dem Token gelesen
me=$(curl -s "$BASE_URL/users/me" -H "Authorization: Bearer $token")
if [ "$me" = "$USER" ]; then
  echo "  OK   /users/me liefert '$USER' aus dem Token"
  pass=$((pass + 1))
else
  echo "  FAIL /users/me liefert '$me' statt '$USER'"
  fail=$((fail + 1))
fi

echo ""
echo "Ergebnis: $pass OK, $fail FAIL"
[ "$fail" -eq 0 ] || exit 1
