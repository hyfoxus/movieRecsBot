package com.gnemirko.imdbvec.config;

import com.pgvector.PGvector;
import org.postgresql.PGConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
@EnableTransactionManagement
public class PgVectorConfig {

    @Bean
    @Primary
    public DataSource pgVectorAwareDataSource(DataSource dataSource) {
        return new DelegatingDataSource(dataSource) {
            private Connection prepare(Connection connection) throws SQLException {
                try {
                    PGConnection pgConnection = connection.unwrap(PGConnection.class);
                    if (pgConnection != null) {
                        pgConnection.addDataType("vector", PGvector.class);
                    }
                } catch (Exception ignored) {
                }
                return connection;
            }

            @Override
            public Connection getConnection() throws SQLException {
                return prepare(super.getConnection());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return prepare(super.getConnection(username, password));
            }
        };
    }
}
