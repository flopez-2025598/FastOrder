#!/usr/bin/env bash
# =============================================================================
#  test-fastorder.sh - Pruebas de integración y estrés de la API FastOrder
#
#  Requisitos: bash + curl (funciona en Linux, macOS y Git Bash de Windows).
#  Uso:
#     ./test-fastorder.sh
#     BASE_URL=http://localhost:8080 STRESS_ORDERS=50 ./test-fastorder.sh
#
#  La API debe estar corriendo con los datos de data.sql
#  (admin@fastorder.com / Admin123*, repartidor@fastorder.com / Repartidor123*,
#   cliente.demo@fastorder.com / Cliente123*).
# =============================================================================

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
ADMIN_PASS="${ADMIN_PASS:-Admin123*}"
REPARTIDOR_PASS="${REPARTIDOR_PASS:-Repartidor123*}"
CLIENTE_PASS="${CLIENTE_PASS:-Cliente123*}"
PASSWORD="Prueba123*"                        # para los usuarios que registra este script

STRESS_STOCK="${STRESS_STOCK:-10}"          # stock del producto para la prueba de concurrencia
STRESS_ORDERS="${STRESS_ORDERS:-30}"        # pedidos simultáneos contra ese stock
RACE_REQUESTS="${RACE_REQUESTS:-10}"        # cancelaciones / cambios de estado simultáneos
LOAD_REQUESTS="${LOAD_REQUESTS:-200}"       # peticiones GET en la prueba de carga
LOAD_CONCURRENCY="${LOAD_CONCURRENCY:-20}"  # paralelismo de la prueba de carga

RUN_ID="$(date +%s)$RANDOM"
PASS=0
FAIL=0
FAILED_TESTS=()

TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t fastorder)"
trap 'rm -rf "$TMP_DIR"' EXIT

if [[ -t 1 ]]; then
    GREEN=$'\e[32m'; RED=$'\e[31m'; YELLOW=$'\e[33m'; BLUE=$'\e[34m'; BOLD=$'\e[1m'; RESET=$'\e[0m'
else
    GREEN=""; RED=""; YELLOW=""; BLUE=""; BOLD=""; RESET=""
fi

# -----------------------------------------------------------------------------
# Utilidades
# -----------------------------------------------------------------------------

section() { printf '\n%s== %s ==%s\n' "$BOLD$BLUE" "$1" "$RESET"; }
info()    { printf '   %s%s%s\n' "$YELLOW" "$1" "$RESET"; }

pass() {
    PASS=$((PASS + 1))
    printf '  %s[OK]%s   %s\n' "$GREEN" "$RESET" "$1"
}

fail() {
    FAIL=$((FAIL + 1))
    FAILED_TESTS+=("$1")
    printf '  %s[FAIL]%s %s\n' "$RED" "$RESET" "$1"
    [[ -n "${2:-}" ]] && printf '         %s\n' "$2"
}

# request METHOD PATH [TOKEN] [JSON]  -> deja el resultado en $STATUS y $BODY
request() {
    local method=$1 path=$2 token=${3:-} data=${4:-}
    local args=(-s -o "$TMP_DIR/body" -w '%{http_code}' -X "$method" "$API$path"
                -H 'Content-Type: application/json' --max-time 60)
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    [[ -n "$data" ]] && args+=(--data "$data")
    : > "$TMP_DIR/body"
    STATUS=$(curl "${args[@]}" 2>/dev/null) || STATUS="000"
    BODY=$(cat "$TMP_DIR/body" 2>/dev/null)
}

# json_get CLAVE [JSON] -> primer valor de la clave (sin comillas)
json_get() {
    local key=$1 json=${2-$BODY}
    printf '%s' "$json" \
        | grep -oE "\"$key\"[[:space:]]*:[[:space:]]*(\"[^\"]*\"|[^,}]*)" \
        | head -n 1 \
        | sed -E "s/^\"$key\"[[:space:]]*:[[:space:]]*//; s/^\"//; s/\"$//"
}

# expect_status "descripcion" CODIGO_ESPERADO  (usa el último $STATUS/$BODY)
expect_status() {
    local desc=$1 expected=$2
    if [[ "$STATUS" == "$expected" ]]; then
        pass "$desc -> HTTP $STATUS"
    else
        fail "$desc" "esperado HTTP $expected, recibido HTTP $STATUS: ${BODY:0:250}"
    fi
}

# expect_eq "descripcion" ACTUAL ESPERADO
expect_eq() {
    local desc=$1 actual=$2 expected=$3
    if [[ "$actual" == "$expected" ]]; then
        pass "$desc ($actual)"
    else
        fail "$desc" "esperado '$expected', recibido '$actual'"
    fi
}

# expect_num "descripcion" ACTUAL ESPERADO  (comparación numérica: 81 == 81.00)
expect_num() {
    local desc=$1 actual=$2 expected=$3
    if [[ -n "$actual" ]] && awk -v a="$actual" -v b="$expected" 'BEGIN { exit !((a + 0) == (b + 0)) }'; then
        pass "$desc ($actual)"
    else
        fail "$desc" "esperado $expected, recibido '$actual'"
    fi
}

# login EMAIL PASSWORD -> deja el token en $OUT (y $STATUS/$BODY de la petición)
login() {
    request POST /auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}"
    OUT=$(json_get token)
}

# stock_de COMERCIO_ID PRODUCTO_ID TOKEN
stock_de() {
    request GET "/comercios/$1/productos" "$3"
    local item
    item=$(printf '%s' "$BODY" | grep -oE "\{\"id\":$2,[^}]*\}" | head -n 1)
    json_get stock "$item"
}

# crear_producto COMERCIO_ID NOMBRE PRECIO STOCK -> id en $OUT
crear_producto() {
    request POST "/comercios/$1/productos" "$ADMIN_TOKEN" \
        "{\"nombre\":\"$2\",\"precio\":$3,\"stock\":$4,\"disponible\":true}"
    OUT=$(json_get id)
}

# crear_pedido TOKEN JSON_ITEMS -> id en $OUT
crear_pedido() {
    request POST /pedidos "$1" "{\"items\":$2}"
    OUT=$(json_get id)
}

count_lines() { grep -c "^$1\$" "$2" 2>/dev/null || true; }

now_ms() {
    local t
    t=$(date +%s%N 2>/dev/null)
    if [[ "$t" =~ ^[0-9]+$ && ${#t} -gt 12 ]]; then echo $((t / 1000000)); else echo $(( $(date +%s) * 1000 )); fi
}

# -----------------------------------------------------------------------------
# 0. Servidor disponible
# -----------------------------------------------------------------------------

section "0. Conexión con $BASE_URL"
for _ in $(seq 1 30); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$API/comercios" 2>/dev/null) || code="000"
    [[ "$code" != "000" ]] && break
    sleep 2
done
if [[ "$code" == "000" ]]; then
    printf '%sNo se pudo conectar con %s. Levante la API con ./mvnw spring-boot:run%s\n' "$RED" "$BASE_URL" "$RESET"
    exit 1
fi
pass "API responde (HTTP $code)"

# -----------------------------------------------------------------------------
# 1. Autenticación y registro
# -----------------------------------------------------------------------------

section "1. Autenticación (/api/v1/auth)"

login admin@fastorder.com "$ADMIN_PASS"; ADMIN_TOKEN=$OUT
expect_status "Login ADMIN (data.sql)" 200
expect_eq "Rol del ADMIN en la respuesta" "$(json_get rol)" "ADMIN"
login repartidor@fastorder.com "$REPARTIDOR_PASS"; REPARTIDOR_TOKEN=$OUT
expect_status "Login REPARTIDOR (data.sql)" 200
login cliente.demo@fastorder.com "$CLIENTE_PASS"; CLIENTE_TOKEN=$OUT
expect_status "Login CLIENTE (data.sql)" 200

if [[ -z "$ADMIN_TOKEN" || -z "$REPARTIDOR_TOKEN" || -z "$CLIENTE_TOKEN" ]]; then
    printf '%sNo se pudieron obtener los tokens de los usuarios semilla; revise data.sql.%s\n' "$RED" "$RESET"
    exit 1
fi

request POST /auth/login "" '{"email":"admin@fastorder.com","password":"incorrecta"}'
expect_status "Login con contraseña incorrecta" 401

EMAIL_NUEVO="cliente.$RUN_ID@test.com"
request POST /auth/register "" \
    "{\"nombre\":\"Cliente Test\",\"direccion\":\"Zona 1\",\"telefono\":\"5555-1234\",\"email\":\"$EMAIL_NUEVO\",\"password\":\"$PASSWORD\",\"rol\":\"ADMIN\"}"
expect_status "Registro de usuario nuevo" 201
expect_eq "Registro ignora 'rol' enviado y asigna CLIENTE" "$(json_get rol)" "CLIENTE"
CLIENTE2_TOKEN=$(json_get token)

request POST /auth/register "" "{\"nombre\":\"Otra vez\",\"email\":\"$EMAIL_NUEVO\",\"password\":\"$PASSWORD\"}"
expect_status "Registro con email duplicado" 409

request POST /auth/register "" '{"nombre":"","email":"no-es-email","password":"1"}'
expect_status "Registro con datos inválidos" 400

# -----------------------------------------------------------------------------
# 2. Seguridad JWT y roles
# -----------------------------------------------------------------------------

section "2. Seguridad (JWT stateless + roles)"

request GET /comercios
expect_status "Sin token" 401
request GET /comercios "token.falso.123"
expect_status "Token inválido" 401
request GET /comercios "${CLIENTE_TOKEN}x"
expect_status "Token con firma alterada" 401

COMERCIO_JSON='{"nombre":"Intruso","categoria":"FARMACIA","direccion":"Zona 1"}'
request POST /comercios "$CLIENTE_TOKEN" "$COMERCIO_JSON"
expect_status "CLIENTE no puede crear comercios" 403
request POST /comercios "$REPARTIDOR_TOKEN" "$COMERCIO_JSON"
expect_status "REPARTIDOR no puede crear comercios" 403
request POST /pedidos "$REPARTIDOR_TOKEN" '{"items":[{"productoId":1,"cantidad":1}]}'
expect_status "REPARTIDOR no puede crear pedidos" 403
request GET /pedidos/disponibles "$CLIENTE_TOKEN"
expect_status "CLIENTE no puede ver pedidos disponibles" 403
request GET /pedidos/mis-pedidos "$REPARTIDOR_TOKEN"
expect_status "REPARTIDOR no tiene 'mis-pedidos'" 403
request PATCH /pedidos/1/estado "$CLIENTE_TOKEN" '{"estado":"EN_PREPARACION"}'
expect_status "CLIENTE no puede cambiar estados" 403

# -----------------------------------------------------------------------------
# 3. Comercios y productos
# -----------------------------------------------------------------------------

section "3. Comercios y productos (/api/v1/comercios)"

request GET /comercios "$CLIENTE_TOKEN"
expect_status "Listar comercios activos" 200

request GET "/comercios?categoria=FARMACIA" "$CLIENTE_TOKEN"
expect_status "Filtrar por categoría FARMACIA" 200
OTRAS=$(printf '%s' "$BODY" | grep -oE '"categoria":"[A-Z]+"' | grep -vc '"FARMACIA"' || true)
expect_eq "El filtro solo devuelve FARMACIA (otras categorías)" "$OTRAS" "0"

request GET "/comercios?categoria=NO_EXISTE" "$CLIENTE_TOKEN"
expect_status "Categoría inválida" 400

request POST /comercios "$ADMIN_TOKEN" \
    "{\"nombre\":\"Pruebas $RUN_ID\",\"categoria\":\"RESTAURANTE\",\"direccion\":\"Zona 10\"}"
expect_status "ADMIN crea comercio" 201
COMERCIO_ID=$(json_get id)
expect_eq "Comercio creado abierto por defecto" "$(json_get abierto)" "true"

request POST /comercios "$ADMIN_TOKEN" '{"nombre":"Sin categoria","direccion":"Zona 1"}'
expect_status "Comercio sin categoría" 400

crear_producto "$COMERCIO_ID" "Pizza $RUN_ID" 25.50 10; PROD_A=$OUT
expect_status "ADMIN agrega producto A (Q25.50, stock 10)" 201
crear_producto "$COMERCIO_ID" "Refresco $RUN_ID" 10.00 2; PROD_B=$OUT
expect_status "ADMIN agrega producto B (Q10.00, stock 2)" 201

request POST "/comercios/$COMERCIO_ID/productos" "$ADMIN_TOKEN" '{"nombre":"Malo","precio":-5,"stock":-1}'
expect_status "Producto con precio/stock negativo" 400
request POST "/comercios/999999/productos" "$ADMIN_TOKEN" '{"nombre":"X","precio":1,"stock":1}'
expect_status "Producto en comercio inexistente" 404
request POST "/comercios/$COMERCIO_ID/productos" "$CLIENTE_TOKEN" '{"nombre":"X","precio":1,"stock":1}'
expect_status "CLIENTE no puede agregar productos" 403

request GET "/comercios/$COMERCIO_ID/productos" "$CLIENTE_TOKEN"
expect_status "Ver catálogo del comercio" 200
request GET "/comercios/999999/productos" "$CLIENTE_TOKEN"
expect_status "Catálogo de comercio inexistente" 404

# -----------------------------------------------------------------------------
# 4. Pedidos: total en servidor, stock y rollback
# -----------------------------------------------------------------------------

section "4. Pedidos: cálculo de total, stock y rollback"

# El cliente intenta mandar totales/precios falsos: deben ignorarse
request POST /pedidos "$CLIENTE_TOKEN" \
    "{\"montoTotal\":1,\"costoEnvio\":0,\"items\":[{\"productoId\":$PROD_A,\"cantidad\":2,\"precioUnitario\":0.01,\"subtotal\":0.02},{\"productoId\":$PROD_B,\"cantidad\":1}]}"
expect_status "CLIENTE crea pedido multiproducto" 201
PEDIDO_1=$(json_get id)
expect_eq   "Estado inicial" "$(json_get estado)" "PENDIENTE"
expect_num  "Costo de envío fijo" "$(json_get costoEnvio)" 20.00
expect_num  "Subtotal de productos (2x25.50 + 1x10.00)" "$(json_get subtotalProductos)" 61.00
expect_num  "Monto total calculado en servidor (61.00 + 20.00)" "$(json_get montoTotal)" 81.00
expect_num  "Precio unitario tomado del catálogo" "$(json_get precioUnitario)" 25.50

expect_num "Stock A descontado (10 - 2)" "$(stock_de "$COMERCIO_ID" "$PROD_A" "$CLIENTE_TOKEN")" 8
expect_num "Stock B descontado (2 - 1)"  "$(stock_de "$COMERCIO_ID" "$PROD_B" "$CLIENTE_TOKEN")" 1

# B solo tiene 1: el pedido completo debe fallar y A NO debe descontarse
request POST /pedidos "$CLIENTE_TOKEN" \
    "{\"items\":[{\"productoId\":$PROD_A,\"cantidad\":3},{\"productoId\":$PROD_B,\"cantidad\":5}]}"
expect_status "Pedido con stock insuficiente" 409
expect_num "ROLLBACK: stock A intacto" "$(stock_de "$COMERCIO_ID" "$PROD_A" "$CLIENTE_TOKEN")" 8
expect_num "ROLLBACK: stock B intacto" "$(stock_de "$COMERCIO_ID" "$PROD_B" "$CLIENTE_TOKEN")" 1

request POST /pedidos "$CLIENTE_TOKEN" '{"items":[{"productoId":999999,"cantidad":1}]}'
expect_status "Pedido con producto inexistente" 404
request POST /pedidos "$CLIENTE_TOKEN" '{"items":[]}'
expect_status "Pedido sin productos" 400
request POST /pedidos "$CLIENTE_TOKEN" "{\"items\":[{\"productoId\":$PROD_A,\"cantidad\":0}]}"
expect_status "Pedido con cantidad 0" 400
request POST /pedidos "$CLIENTE_TOKEN" '{"items": esto no es json'
expect_status "JSON mal formado" 400

request GET /pedidos/mis-pedidos "$CLIENTE_TOKEN"
expect_status "Historial mis-pedidos" 200
if printf '%s' "$BODY" | grep -q "{\"id\":$PEDIDO_1,\"clienteId\""; then pass "mis-pedidos contiene el pedido #$PEDIDO_1"; else fail "mis-pedidos contiene el pedido #$PEDIDO_1"; fi

request GET /pedidos/mis-pedidos "$CLIENTE2_TOKEN"
if printf '%s' "$BODY" | grep -q "{\"id\":$PEDIDO_1,\"clienteId\""; then fail "Otro cliente NO ve pedidos ajenos"; else pass "Otro cliente NO ve pedidos ajenos"; fi

request GET "/pedidos/$PEDIDO_1" "$CLIENTE_TOKEN"
expect_status "Seguimiento del propio pedido" 200
request GET "/pedidos/$PEDIDO_1" "$CLIENTE2_TOKEN"
expect_status "Seguimiento de pedido ajeno" 403

# -----------------------------------------------------------------------------
# 5. Flujo estricto de estados
# -----------------------------------------------------------------------------

section "5. Flujo de estados PENDIENTE -> EN_PREPARACION -> EN_CAMINO -> ENTREGADO"

request GET /pedidos/disponibles "$REPARTIDOR_TOKEN"
expect_status "REPARTIDOR consulta disponibles" 200
if printf '%s' "$BODY" | grep -q "{\"id\":$PEDIDO_1,\"clienteId\""; then pass "El pedido #$PEDIDO_1 aparece como disponible"; else fail "El pedido #$PEDIDO_1 aparece como disponible"; fi
request GET /pedidos/disponibles "$ADMIN_TOKEN"
expect_status "ADMIN consulta disponibles" 200

request PATCH "/pedidos/$PEDIDO_1/estado" "$REPARTIDOR_TOKEN" '{"estado":"EN_CAMINO"}'
expect_status "No se puede saltar PENDIENTE -> EN_CAMINO" 400
request PATCH "/pedidos/$PEDIDO_1/estado" "$REPARTIDOR_TOKEN" '{"estado":"ENTREGADO"}'
expect_status "No se puede saltar PENDIENTE -> ENTREGADO" 400
request PATCH "/pedidos/$PEDIDO_1/estado" "$REPARTIDOR_TOKEN" '{"estado":"VOLANDO"}'
expect_status "Estado inexistente" 400

request PATCH "/pedidos/$PEDIDO_1/estado" "$REPARTIDOR_TOKEN" '{"estado":"EN_PREPARACION"}'
expect_status "PENDIENTE -> EN_PREPARACION" 200
expect_eq "Estado actualizado" "$(json_get estado)" "EN_PREPARACION"
if [[ -n "$(json_get repartidorId)" && "$(json_get repartidorId)" != "null" ]]; then
    pass "El repartidor quedó asignado al pedido"
else
    fail "El repartidor quedó asignado al pedido" "repartidorId vacío"
fi

request PATCH "/pedidos/$PEDIDO_1/cancelar" "$CLIENTE_TOKEN"
expect_status "CLIENTE no puede cancelar si ya no está PENDIENTE" 400
request PATCH "/pedidos/$PEDIDO_1/estado" "$ADMIN_TOKEN" '{"estado":"CANCELADO"}'
expect_status "CANCELADO no se permite por /estado" 400
request PATCH "/pedidos/$PEDIDO_1/estado" "$REPARTIDOR_TOKEN" '{"estado":"PENDIENTE"}'
expect_status "No se puede regresar a PENDIENTE" 400

request PATCH "/pedidos/$PEDIDO_1/estado" "$REPARTIDOR_TOKEN" '{"estado":"EN_CAMINO"}'
expect_status "EN_PREPARACION -> EN_CAMINO" 200
request PATCH "/pedidos/$PEDIDO_1/estado" "$REPARTIDOR_TOKEN" '{"estado":"ENTREGADO"}'
expect_status "EN_CAMINO -> ENTREGADO" 200
expect_eq "Estado final" "$(json_get estado)" "ENTREGADO"

request PATCH "/pedidos/$PEDIDO_1/estado" "$REPARTIDOR_TOKEN" '{"estado":"EN_CAMINO"}'
expect_status "Pedido ENTREGADO no puede cambiar" 400
request PATCH "/pedidos/999999/estado" "$REPARTIDOR_TOKEN" '{"estado":"EN_PREPARACION"}'
expect_status "Cambiar estado de pedido inexistente" 404

# -----------------------------------------------------------------------------
# 6. Cancelación y restauración de stock
# -----------------------------------------------------------------------------

section "6. Cancelación (solo PENDIENTE) y restauración de stock"

crear_pedido "$CLIENTE_TOKEN" "[{\"productoId\":$PROD_A,\"cantidad\":3}]"; PEDIDO_2=$OUT
expect_status "Pedido para cancelar" 201
expect_num "Stock A tras pedido (8 - 3)" "$(stock_de "$COMERCIO_ID" "$PROD_A" "$CLIENTE_TOKEN")" 5

request PATCH "/pedidos/$PEDIDO_2/cancelar" "$CLIENTE2_TOKEN"
expect_status "Otro cliente no puede cancelar pedido ajeno" 403
request PATCH "/pedidos/$PEDIDO_2/cancelar" "$REPARTIDOR_TOKEN"
expect_status "REPARTIDOR no puede cancelar" 403

request PATCH "/pedidos/$PEDIDO_2/cancelar" "$CLIENTE_TOKEN"
expect_status "CLIENTE cancela su pedido PENDIENTE" 200
expect_eq "Estado" "$(json_get estado)" "CANCELADO"
expect_num "Stock A restaurado (5 + 3)" "$(stock_de "$COMERCIO_ID" "$PROD_A" "$CLIENTE_TOKEN")" 8

request PATCH "/pedidos/$PEDIDO_2/cancelar" "$CLIENTE_TOKEN"
expect_status "No se puede cancelar dos veces" 400
expect_num "Stock A no se restaura dos veces" "$(stock_de "$COMERCIO_ID" "$PROD_A" "$CLIENTE_TOKEN")" 8

crear_pedido "$CLIENTE2_TOKEN" "[{\"productoId\":$PROD_A,\"cantidad\":1}]"; PEDIDO_3=$OUT
request PATCH "/pedidos/$PEDIDO_3/cancelar" "$ADMIN_TOKEN"
expect_status "ADMIN cancela pedido de un cliente" 200
expect_num "Stock A restaurado por cancelación del ADMIN" "$(stock_de "$COMERCIO_ID" "$PROD_A" "$CLIENTE_TOKEN")" 8

request GET "/pedidos?estado=CANCELADO" "$ADMIN_TOKEN"
expect_status "ADMIN audita pedidos cancelados" 200
request GET /pedidos "$CLIENTE_TOKEN"
expect_status "CLIENTE no accede a la auditoría" 403

# -----------------------------------------------------------------------------
# 7. Estrés: concurrencia sobre el stock
# -----------------------------------------------------------------------------

section "7. Estrés: $STRESS_ORDERS pedidos simultáneos contra stock $STRESS_STOCK"

crear_producto "$COMERCIO_ID" "Promo $RUN_ID" 9.99 "$STRESS_STOCK"; PROD_STRESS=$OUT
expect_status "Producto para estrés" 201

STRESS_BODY="{\"items\":[{\"productoId\":$PROD_STRESS,\"cantidad\":1}]}"
T0=$(now_ms)
for i in $(seq 1 "$STRESS_ORDERS"); do
    curl -s -o /dev/null -w '%{http_code}\n' --max-time 60 -X POST "$API/pedidos" \
        -H 'Content-Type: application/json' -H "Authorization: Bearer $CLIENTE_TOKEN" \
        --data "$STRESS_BODY" > "$TMP_DIR/stress_$i" 2>/dev/null &
done
wait
T1=$(now_ms)
cat "$TMP_DIR"/stress_* > "$TMP_DIR/stress_all"

CREADOS=$(count_lines 201 "$TMP_DIR/stress_all")
RECHAZADOS=$(count_lines 409 "$TMP_DIR/stress_all")
ESPERADOS_OK=$(( STRESS_ORDERS < STRESS_STOCK ? STRESS_ORDERS : STRESS_STOCK ))
info "Tiempo: $((T1 - T0)) ms | 201: $CREADOS | 409: $RECHAZADOS | otros: $((STRESS_ORDERS - CREADOS - RECHAZADOS))"

expect_eq "Pedidos aceptados = stock disponible" "$CREADOS" "$ESPERADOS_OK"
expect_eq "Pedidos rechazados por stock (409)" "$RECHAZADOS" "$((STRESS_ORDERS - ESPERADOS_OK))"
expect_num "Stock final sin sobreventa" "$(stock_de "$COMERCIO_ID" "$PROD_STRESS" "$CLIENTE_TOKEN")" $((STRESS_STOCK - ESPERADOS_OK))

# --- Cancelaciones simultáneas del mismo pedido: solo una debe ganar ---
crear_producto "$COMERCIO_ID" "Carrera $RUN_ID" 5.00 10; PROD_RACE=$OUT
crear_pedido "$CLIENTE_TOKEN" "[{\"productoId\":$PROD_RACE,\"cantidad\":4}]"; PEDIDO_RACE=$OUT
for i in $(seq 1 "$RACE_REQUESTS"); do
    curl -s -o /dev/null -w '%{http_code}\n' --max-time 60 -X PATCH "$API/pedidos/$PEDIDO_RACE/cancelar" \
        -H "Authorization: Bearer $CLIENTE_TOKEN" > "$TMP_DIR/cancel_$i" 2>/dev/null &
done
wait
cat "$TMP_DIR"/cancel_* > "$TMP_DIR/cancel_all"
expect_eq "$RACE_REQUESTS cancelaciones simultáneas: solo 1 exitosa" "$(count_lines 200 "$TMP_DIR/cancel_all")" "1"
expect_num "Stock restaurado una sola vez" "$(stock_de "$COMERCIO_ID" "$PROD_RACE" "$CLIENTE_TOKEN")" 10

# --- Cambios de estado simultáneos: solo un repartidor/admin avanza el pedido ---
crear_pedido "$CLIENTE_TOKEN" "[{\"productoId\":$PROD_RACE,\"cantidad\":1}]"; PEDIDO_RACE2=$OUT
for i in $(seq 1 "$RACE_REQUESTS"); do
    curl -s -o /dev/null -w '%{http_code}\n' --max-time 60 -X PATCH "$API/pedidos/$PEDIDO_RACE2/estado" \
        -H 'Content-Type: application/json' -H "Authorization: Bearer $REPARTIDOR_TOKEN" \
        --data '{"estado":"EN_PREPARACION"}' > "$TMP_DIR/estado_$i" 2>/dev/null &
done
wait
cat "$TMP_DIR"/estado_* > "$TMP_DIR/estado_all"
expect_eq "$RACE_REQUESTS cambios de estado simultáneos: solo 1 exitoso" "$(count_lines 200 "$TMP_DIR/estado_all")" "1"

# -----------------------------------------------------------------------------
# 8. Estrés: carga de lectura
# -----------------------------------------------------------------------------

section "8. Carga: $LOAD_REQUESTS GET /comercios con $LOAD_CONCURRENCY en paralelo"

T0=$(now_ms)
seq 1 "$LOAD_REQUESTS" | xargs -P "$LOAD_CONCURRENCY" -I{} \
    curl -s -o /dev/null -w '%{http_code}\n' --max-time 30 \
    -H "Authorization: Bearer $CLIENTE_TOKEN" "$API/comercios" > "$TMP_DIR/load" 2>/dev/null
T1=$(now_ms)
LOAD_OK=$(count_lines 200 "$TMP_DIR/load")
DUR=$((T1 - T0)); [[ $DUR -le 0 ]] && DUR=1
info "Tiempo: ${DUR} ms | ~$(( LOAD_REQUESTS * 1000 / DUR )) req/s"
expect_eq "Todas las peticiones respondieron 200" "$LOAD_OK" "$LOAD_REQUESTS"

# -----------------------------------------------------------------------------
# Resumen
# -----------------------------------------------------------------------------

TOTAL=$((PASS + FAIL))
section "Resumen"
printf '  Total: %d   %sOK: %d%s   %sFALLIDAS: %d%s\n' "$TOTAL" "$GREEN" "$PASS" "$RESET" "$RED" "$FAIL" "$RESET"
if [[ $FAIL -gt 0 ]]; then
    printf '\n  Pruebas fallidas:\n'
    for t in "${FAILED_TESTS[@]}"; do printf '   - %s\n' "$t"; done
    exit 1
fi
printf '\n  %sTodas las pruebas pasaron.%s\n' "$GREEN$BOLD" "$RESET"
exit 0
