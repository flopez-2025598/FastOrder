package com.fareslopez.fastorder.pedido;

import com.fareslopez.fastorder.comercio.entity.Categoria;
import com.fareslopez.fastorder.comercio.entity.Comercio;
import com.fareslopez.fastorder.comercio.entity.Producto;
import com.fareslopez.fastorder.comercio.repository.ComercioRepository;
import com.fareslopez.fastorder.comercio.repository.ProductoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración end-to-end: levantan el servidor en un puerto aleatorio y
 * ejercitan la API por HTTP real (cadena de filtros JWT incluida) contra H2 en memoria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PedidoIntegrationTest {

    private static final String PASSWORD = "Prueba123*"; // usuarios registrados en las pruebas

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ComercioRepository comercioRepository;

    @Autowired
    private ProductoRepository productoRepository;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String adminToken;
    private String repartidorToken;
    private String clienteToken;

    @BeforeEach
    void login() throws Exception {
        adminToken = token("admin@fastorder.com", "Admin123*");
        repartidorToken = token("repartidor@fastorder.com", "Repartidor123*");
        clienteToken = token("cliente.demo@fastorder.com", "Cliente123*");
    }

    // =====================================================================
    // Seguridad
    // =====================================================================

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(call("GET", "/api/v1/comercios", null, null).status()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/comercios", "token.invalido.xyz", null).status()).isEqualTo(401);
    }

    @Test
    void controlDeAccesoPorRol() throws Exception {
        Map<String, Object> comercio = Map.of("nombre", "No permitido", "categoria", "FARMACIA", "direccion", "Zona 1");
        assertThat(call("POST", "/api/v1/comercios", clienteToken, comercio).status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/comercios", adminToken, comercio).status()).isEqualTo(201);

        assertThat(call("GET", "/api/v1/pedidos/disponibles", clienteToken, null).status()).isEqualTo(403);
        assertThat(call("GET", "/api/v1/pedidos/disponibles", repartidorToken, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/v1/pedidos/mis-pedidos", repartidorToken, null).status()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/pedidos", repartidorToken,
                Map.of("items", List.of(Map.of("productoId", 1, "cantidad", 1)))).status()).isEqualTo(403);
    }

    @Test
    void registroSiempreAsignaRolCliente() throws Exception {
        String email = "nuevo-" + UUID.randomUUID() + "@test.com";
        Resp r = call("POST", "/api/v1/auth/register", null, Map.of(
                "nombre", "Nuevo", "email", email, "password", PASSWORD, "rol", "ADMIN"));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("rol").asText()).isEqualTo("CLIENTE");
        assertThat(r.body().get("token").asText()).isNotBlank();

        assertThat(call("POST", "/api/v1/auth/register", null, Map.of(
                "nombre", "Nuevo", "email", email, "password", PASSWORD)).status()).isEqualTo(409);
    }

    // =====================================================================
    // Reglas de negocio
    // =====================================================================

    @Test
    void totalSeCalculaEnServidorYSeDescuentaStock() throws Exception {
        Producto a = nuevoProducto("25.50", 10);
        Producto b = nuevoProducto("10.00", 5);

        // El cliente intenta mandar un total falso: debe ignorarse
        Resp r = call("POST", "/api/v1/pedidos", clienteToken, Map.of(
                "montoTotal", 1,
                "items", List.of(
                        Map.of("productoId", a.getId(), "cantidad", 2, "precioUnitario", 0.01),
                        Map.of("productoId", b.getId(), "cantidad", 1))));

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("estado").asText()).isEqualTo("PENDIENTE");
        assertDecimal(r.body().get("costoEnvio"), "20.00");
        assertDecimal(r.body().get("subtotalProductos"), "61.00");
        assertDecimal(r.body().get("montoTotal"), "81.00"); // 2*25.50 + 1*10.00 + 20.00
        assertThat(r.body().get("detalles").size()).isEqualTo(2);

        assertThat(stock(a.getId())).isEqualTo(8);
        assertThat(stock(b.getId())).isEqualTo(4);
    }

    @Test
    void stockInsuficienteHaceRollbackCompleto() throws Exception {
        Producto a = nuevoProducto("15.00", 10);
        Producto b = nuevoProducto("8.00", 1);

        Resp r = call("POST", "/api/v1/pedidos", clienteToken, Map.of("items", List.of(
                Map.of("productoId", a.getId(), "cantidad", 3),
                Map.of("productoId", b.getId(), "cantidad", 2))));

        assertThat(r.status()).isEqualTo(409);
        // Ningún stock se descontó, ni siquiera el del producto que sí tenía existencias
        assertThat(stock(a.getId())).isEqualTo(10);
        assertThat(stock(b.getId())).isEqualTo(1);
    }

    @Test
    void validacionesDelPedido() throws Exception {
        assertThat(call("POST", "/api/v1/pedidos", clienteToken, Map.of("items", List.of())).status())
                .isEqualTo(400);
        assertThat(call("POST", "/api/v1/pedidos", clienteToken,
                Map.of("items", List.of(Map.of("productoId", 1, "cantidad", 0)))).status()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/pedidos", clienteToken,
                Map.of("items", List.of(Map.of("productoId", 999999, "cantidad", 1)))).status()).isEqualTo(404);
    }

    @Test
    void flujoEstrictoDeEstados() throws Exception {
        Producto p = nuevoProducto("30.00", 10);
        long pedidoId = crearPedido(clienteToken, p.getId(), 1);
        String url = "/api/v1/pedidos/" + pedidoId + "/estado";

        // No se puede saltar de PENDIENTE a EN_CAMINO
        assertThat(call("PATCH", url, repartidorToken, Map.of("estado", "EN_CAMINO")).status()).isEqualTo(400);

        Resp prep = call("PATCH", url, repartidorToken, Map.of("estado", "EN_PREPARACION"));
        assertThat(prep.status()).isEqualTo(200);
        assertThat(prep.body().get("estado").asText()).isEqualTo("EN_PREPARACION");
        assertThat(prep.body().get("repartidorId").isNull()).isFalse(); // el repartidor tomó el pedido

        // Ya no está PENDIENTE: el cliente no puede cancelarlo
        assertThat(call("PATCH", "/api/v1/pedidos/" + pedidoId + "/cancelar", clienteToken, null).status())
                .isEqualTo(400);
        // CANCELADO no se acepta por /estado
        assertThat(call("PATCH", url, adminToken, Map.of("estado", "CANCELADO")).status()).isEqualTo(400);

        assertThat(call("PATCH", url, repartidorToken, Map.of("estado", "EN_CAMINO")).status()).isEqualTo(200);
        Resp entregado = call("PATCH", url, repartidorToken, Map.of("estado", "ENTREGADO"));
        assertThat(entregado.status()).isEqualTo(200);
        assertThat(entregado.body().get("estado").asText()).isEqualTo("ENTREGADO");

        // Estado final: no se puede retroceder
        assertThat(call("PATCH", url, repartidorToken, Map.of("estado", "EN_CAMINO")).status()).isEqualTo(400);
        assertThat(call("PATCH", "/api/v1/pedidos/999999/estado", repartidorToken,
                Map.of("estado", "EN_PREPARACION")).status()).isEqualTo(404);
    }

    @Test
    void cancelarRestauraStockYSoloElDuenoPuede() throws Exception {
        Producto p = nuevoProducto("12.00", 10);
        long pedidoId = crearPedido(clienteToken, p.getId(), 4);
        assertThat(stock(p.getId())).isEqualTo(6);

        String otroCliente = registrarCliente();
        assertThat(call("PATCH", "/api/v1/pedidos/" + pedidoId + "/cancelar", otroCliente, null).status())
                .isEqualTo(403);
        assertThat(call("GET", "/api/v1/pedidos/" + pedidoId, otroCliente, null).status()).isEqualTo(403);

        Resp r = call("PATCH", "/api/v1/pedidos/" + pedidoId + "/cancelar", clienteToken, null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("estado").asText()).isEqualTo("CANCELADO");
        assertThat(stock(p.getId())).isEqualTo(10);

        // Doble cancelación no devuelve stock dos veces
        assertThat(call("PATCH", "/api/v1/pedidos/" + pedidoId + "/cancelar", clienteToken, null).status())
                .isEqualTo(400);
        assertThat(stock(p.getId())).isEqualTo(10);
    }

    @Test
    void misPedidosSoloDevuelveLosDelCliente() throws Exception {
        Producto p = nuevoProducto("5.00", 10);
        String nuevo = registrarCliente();
        long pedidoId = crearPedido(nuevo, p.getId(), 1);

        Resp r = call("GET", "/api/v1/pedidos/mis-pedidos", nuevo, null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().size()).isEqualTo(1);
        assertThat(r.body().get(0).get("id").asLong()).isEqualTo(pedidoId);
    }

    // =====================================================================
    // Concurrencia: nunca se vende más stock del que existe
    // =====================================================================

    @Test
    void pedidosConcurrentesNoSobrevendenStock() throws Exception {
        int stockInicial = 5;
        int peticiones = 20;
        Producto p = nuevoProducto("9.99", stockInicial);
        Map<String, Object> body = Map.of("items", List.of(Map.of("productoId", p.getId(), "cantidad", 1)));

        ExecutorService pool = Executors.newFixedThreadPool(peticiones);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < peticiones; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return call("POST", "/api/v1/pedidos", clienteToken, body).status();
            }));
        }
        salida.countDown();

        int creados = 0;
        for (Future<Integer> f : resultados) {
            int status = f.get(60, TimeUnit.SECONDS);
            assertThat(status).isIn(201, 409);
            if (status == 201) {
                creados++;
            }
        }
        pool.shutdown();

        int stockFinal = stock(p.getId());
        assertThat(stockFinal).isGreaterThanOrEqualTo(0);
        assertThat(creados).isEqualTo(stockInicial - stockFinal);
        assertThat(creados).isLessThanOrEqualTo(stockInicial);
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    private record Resp(int status, JsonNode body) {
    }

    private Resp call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = (body == null)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        builder.method(method, publisher);

        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String text = response.body();
        JsonNode json = (text == null || text.isBlank()) ? objectMapper.nullNode() : objectMapper.readTree(text);
        return new Resp(response.statusCode(), json);
    }

    private String token(String email, String password) throws Exception {
        Resp r = call("POST", "/api/v1/auth/login", null, Map.of("email", email, "password", password));
        assertThat(r.status()).as("login de " + email).isEqualTo(200);
        return r.body().get("token").asText();
    }

    private String registrarCliente() throws Exception {
        Resp r = call("POST", "/api/v1/auth/register", null, Map.of(
                "nombre", "Cliente Test", "email", "c-" + UUID.randomUUID() + "@test.com", "password", PASSWORD));
        assertThat(r.status()).isEqualTo(201);
        return r.body().get("token").asText();
    }

    private long crearPedido(String token, Long productoId, int cantidad) throws Exception {
        Resp r = call("POST", "/api/v1/pedidos", token,
                Map.of("items", List.of(Map.of("productoId", productoId, "cantidad", cantidad))));
        assertThat(r.status()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    private Producto nuevoProducto(String precio, int stock) {
        Comercio comercio = new Comercio();
        comercio.setNombre("Comercio Test " + UUID.randomUUID().toString().substring(0, 8));
        comercio.setCategoria(Categoria.RESTAURANTE);
        comercio.setDireccion("Zona 1");
        comercio.setAbierto(true);
        comercio = comercioRepository.save(comercio);

        Producto producto = new Producto();
        producto.setComercio(comercio);
        producto.setNombre("Producto Test " + UUID.randomUUID().toString().substring(0, 8));
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(stock);
        producto.setDisponible(true);
        return productoRepository.save(producto);
    }

    private int stock(Long productoId) {
        return productoRepository.findById(productoId).orElseThrow().getStock();
    }

    private static void assertDecimal(JsonNode node, String esperado) {
        assertThat(node.decimalValue()).isEqualByComparingTo(new BigDecimal(esperado));
    }
}
