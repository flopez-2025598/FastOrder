package com.fareslopez.fastorder.common.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crea automáticamente la base de datos de PostgreSQL (por defecto "fastorder_db") si no existe,
 * ANTES de que Spring/Hibernate intenten conectarse. Así basta con ejecutar la aplicación:
 * la base se crea aquí, Hibernate crea las tablas (ddl-auto=update) y data.sql carga los datos.
 *
 * Se registra en META-INF/spring.factories. Si la URL no es de PostgreSQL (p. ej. H2 en las
 * pruebas) no hace nada.
 */
public class PostgresDatabaseCreator implements EnvironmentPostProcessor {

    // jdbc:postgresql://host:puerto/nombre_bd?parametros
    private static final Pattern POSTGRES_URL =
            Pattern.compile("^(jdbc:postgresql://[^/]+/)([^?;/]+)(.*)$");
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final String SQLSTATE_BD_DUPLICADA = "42P04";

    private final Log log;

    public PostgresDatabaseCreator(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(PostgresDatabaseCreator.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean habilitado = environment.getProperty("fastorder.db.auto-create", Boolean.class, true);
        String url = environment.getProperty("spring.datasource.url");
        if (!habilitado || url == null) {
            return;
        }

        Matcher matcher = POSTGRES_URL.matcher(url.trim());
        if (!matcher.matches()) {
            return; // no es PostgreSQL (H2 en pruebas, etc.)
        }

        String nombreBd = matcher.group(2);
        if (!NOMBRE_VALIDO.matcher(nombreBd).matches()) {
            log.warn("Nombre de base de datos no soportado para creación automática: " + nombreBd);
            return;
        }

        // Se conecta a la base de mantenimiento "postgres", que siempre existe
        String urlAdmin = matcher.group(1) + "postgres" + matcher.group(3);
        String usuario = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        DriverManager.setLoginTimeout(10);
        try (Connection conn = DriverManager.getConnection(urlAdmin, usuario, password)) {
            if (existeBaseDeDatos(conn, nombreBd)) {
                log.info("Base de datos '" + nombreBd + "' encontrada");
                return;
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE \"" + nombreBd + "\"");
                log.info("Base de datos '" + nombreBd + "' creada automáticamente");
            }
        } catch (SQLException e) {
            if (SQLSTATE_BD_DUPLICADA.equals(e.getSQLState())) {
                return; // otra instancia la creó al mismo tiempo
            }
            // No se detiene el arranque aquí: si la base de verdad no está disponible,
            // Spring mostrará el error de conexión correspondiente.
            log.warn("No se pudo verificar/crear la base de datos '" + nombreBd + "' automáticamente: "
                    + e.getMessage() + ". Verifique que PostgreSQL esté encendido y las credenciales en "
                    + "application.properties.");
        }
    }

    private boolean existeBaseDeDatos(Connection conn, String nombreBd) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, nombreBd);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
