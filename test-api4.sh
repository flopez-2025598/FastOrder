#!/bin/bash


# CONFIGURACIÓN GENERAL

BASE_URL="http://localhost:8080/api/v1"
ADMIN_EMAIL="admin@fastorder.com"
ADMIN_PASS="Admin123*"
CLIENTE_EMAIL="cliente@fastorder.com"
CLIENTE_PASS="Cliente123*"


echo " INICIANDO PRUEBAS UNITARIAS (DELIVERY)"



# 1. REGISTRO Y AUTENTICACIÓN


echo -e "\n[1] Registrando usuario CLIENTE..."
curl -s -X POST "$BASE_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Cliente Ejemplo",
    "direccion": "Zona 10, Ciudad",
    "telefono": "55554321",
    "email": "'"$CLIENTE_EMAIL"'",
    "password": "'"$CLIENTE_PASS"'"
  }' | jq .

echo -e "\n[2] Autenticando usuario ADMIN..."
ADMIN_LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "'"$ADMIN_EMAIL"'",
    "password": "'"$ADMIN_PASS"'"
  }')

ADMIN_TOKEN=$(echo $ADMIN_LOGIN_RESP | jq -r '.token // .accessToken')

if [ "$ADMIN_TOKEN" == "null" ] || [ -z "$ADMIN_TOKEN" ]; then
  echo " Error al obtener el token de ADMIN. Revisa credenciales o endpoint /auth/login."
  exit 1
fi

echo " Token Admin Obtenido: ${ADMIN_TOKEN:0:20}..."


# 2. CREACIÓN DE COMERCIO Y PRODUCTOS (Rol ADMIN)


echo -e "\n[3] Registrando Comercio..."
COMERCIO_RESP=$(curl -s -X POST "$BASE_URL/comercios" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "nombre": "Burger Express",
    "categoria": "RESTAURANTE",
    "direccion": "Calzada Roosevelt 12-45",
    "abierto": true
  }')

echo $COMERCIO_RESP | jq .
COMERCIO_ID=$(echo $COMERCIO_RESP | jq -r '.id // 1')

echo -e "\n[4] Registrando Producto en Comercio ID $COMERCIO_ID..."
PRODUCTO_RESP=$(curl -s -X POST "$BASE_URL/comercios/$COMERCIO_ID/productos" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "nombre": "Hamburguesa Doble con Queso",
    "precio": 45.00,
    "stock": 50,
    "disponible": true
  }')

echo $PRODUCTO_RESP | jq .
PRODUCTO_ID=$(echo $PRODUCTO_RESP | jq -r '.id // 1')


# 3. CREACIÓN DE PEDIDO (Rol CLIENTE)


echo -e "\n[5] Autenticando usuario CLIENTE..."
CLIENTE_LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "'"$CLIENTE_EMAIL"'",
    "password": "'"$CLIENTE_PASS"'"
  }')

CLIENTE_TOKEN=$(echo $CLIENTE_LOGIN_RESP | jq -r '.token // .accessToken')

echo -e "\n[6] Creando un nuevo pedido (Rol CLIENTE)..."
PEDIDO_RESP=$(curl -s -X POST "$BASE_URL/pedidos" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CLIENTE_TOKEN" \
  -d '{
    "items": [
      {
        "productoId": '$PRODUCTO_ID',
        "cantidad": 2
      }
    ]
  }')

echo $PEDIDO_RESP | jq .


# 4. PRUEBA DE CONTROL DE ACCESO (403 Forbidden)


echo -e "\n[7] Intentando crear un comercio con Rol CLIENTE (Debe fallar con 403 Forbidden)..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/comercios" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CLIENTE_TOKEN" \
  -d '{
    "nombre": "Tienda Ilegal",
    "categoria": "SUPERMERCADO",
    "direccion": "Desconocida",
    "abierto": true
  }')

if [ "$HTTP_STATUS" -eq 403 ]; then
  echo "--> Seguridad Validada: Recibido Status 403 Forbidden correctamente."
else
  echo "--> Advertencia: Se esperaba 403 pero se obtuvo Status $HTTP_STATUS."
fi


# 5. PRUEBA DE ESTRÉS Y CONCURRENCIA


echo -e "\n"
echo " --> EJECUTANDO PRUEBA DE ESTRÉS EN CATÁLOGO DE COMERCIOS"
echo " "

if command -v ab &> /dev/null; then
  ab -n 500 -c 50 -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE_URL/comercios"
else
  seq 100 | xargs -n 1 -P 10 -I {} curl -s -o /dev/null -w "%{http_code}\n" \
    -X GET "$BASE_URL/comercios" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | sort | uniq -c
fi

echo -e "\n"
echo " --> PRUEBAS COMPLETADAS"
echo " "