package com.freecharge.smsprofilerservice.config;


import com.freecharge.vault.PropertiesConfig;
import com.freecharge.vault.VaultConnection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.dialect.Dialect;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.vault.authentication.SessionManager;

import javax.sql.DataSource;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories("com.freecharge.smsprofilerservice.dao.mysql")
public class MysqlConfig {

    private final Map<String, Object> dbProperties;

    private final Environment environment;

    private HikariDataSource datasource;

    private final SessionManager sessionManager;

    private final VaultConnection vaultConnection;

    @Value("${sms.ml.fetchCredsFromVault}")
    private boolean fetchCredsFromVault;

    @Autowired
    public MysqlConfig(@Qualifier("databaseProperties") final PropertiesConfig dbProperties,
                       @NonNull final Environment environment,
                       @NonNull final VaultConnection vaultConnection,
                       @NonNull final SessionManager sessionManager) {
        this.dbProperties = dbProperties.getProperties();
        this.environment = environment;
        this.vaultConnection = vaultConnection;
        this.sessionManager = sessionManager;
    }

    @Bean
    public HibernateTemplate getHibernateTemplate() {
        return new HibernateTemplate(sessionFactory().getObject());
    }

    @Bean(name = "entityManagerFactory")
    public LocalSessionFactoryBean sessionFactory() {
        final LocalSessionFactoryBean sessionFactoryBean = new LocalSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource());
        sessionFactoryBean.setHibernateProperties(hibernateProperties());
        sessionFactoryBean.setPackagesToScan("com.freecharge.smsprofilerservice.dao.mysql");
        return sessionFactoryBean;
    }

    @Bean
    @RefreshScope
    public DataSource dataSource() {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl((String) dbProperties.get("hibernate.connection.url"));
        final Map<String, String> dbCredMapFromVault = getDBCredentialsFromVault(false);
        final String username = dbCredMapFromVault.get("username");
        log.info("UserName DB While System Startup is {} and password is {} , for date {}", username, dbCredMapFromVault.get("password"), new Date(System.currentTimeMillis()));
        config.setUsername(username);
        config.setPassword(dbCredMapFromVault.get("password"));
        config.setMaximumPoolSize((Integer) dbProperties.get("hibernate.maxpool.size"));
        config.setMinimumIdle((Integer) dbProperties.get("hibernate.minimum.idle"));
        datasource = new HikariDataSource(config);
        return datasource;
    }

    @Scheduled(initialDelayString = "${spring.datasource.refresh}", fixedDelayString = "${spring.datasource.refresh}")
    public void updateDataSource() throws JSONException {
        log.info("Updating DB Connections value.....");
        if (datasource != null) {
            final HikariPoolMXBean hikariPoolMXBean = datasource.getHikariPoolMXBean();
            log.info("SQL Connections Before Eviction are active : {}, idle are : {}",
                    hikariPoolMXBean.getActiveConnections(), hikariPoolMXBean.getIdleConnections());
            if (hikariPoolMXBean != null) {
                hikariPoolMXBean.softEvictConnections();
                log.info("SQL Connections Soft Eviction are active : {}, idle are : {}",
                        hikariPoolMXBean.getActiveConnections(), hikariPoolMXBean.getIdleConnections());
            }
            final HikariConfigMXBean hikariConfigMXBean = datasource.getHikariConfigMXBean();
            final Map<String, String> dbCredMap = getDBCredentialsFromVault(true);
            log.info("Refresh Creds UserName is {} and password is : {}", dbCredMap.get("username"), dbCredMap.get("password"));
            hikariConfigMXBean.setUsername(dbCredMap.get("username"));
            hikariConfigMXBean.setPassword(dbCredMap.get("password"));
            log.info("SQL Connections After Eviction are active : {} idle are : {}",
                    hikariPoolMXBean.getActiveConnections(), hikariPoolMXBean.getIdleConnections());
//            log.info("Value : {}", ToStringBuilder.reflectionToString(datasource));
            log.info("Updated DB Connections value.....");
            log.info("String similarity pass value is : {}", environment.getProperty("string.similarity.pass.value"));
        }
    }

    private Map<String, String> getDBCredentialsFromVault(boolean refreshFlag) {
        final Map<String, String> vaultCredMap = new HashMap<>();
        String userName;
        String password;
        Map<String, String> dbCredsMap = new HashMap<>();
        if (fetchCredsFromVault) {
            String dbRole = (String) dbProperties.get("vault.db.role");
            if (!refreshFlag) {
                dbCredsMap = vaultConnection.fetchDBCredsFromVault(
                        sessionManager.getSessionToken().getToken(), dbRole);
            }
            if(refreshFlag){
                dbCredsMap = vaultConnection.fetchDBCredsFromVault(
                        vaultConnection.fetchNewTokenFromVault(), dbRole);
            }
            userName = dbCredsMap.get("username");
            password = dbCredsMap.get("password");
        } else {
            userName = (String) dbProperties.get("hibernate.connection.username");
            password = (String) dbProperties.get("hibernate.connection.password");
        }
        vaultCredMap.put("username", userName);
        vaultCredMap.put("password", password);
        return vaultCredMap;
    }

    @Bean
    @Autowired
    public HibernateTransactionManager transactionManager(@NonNull final SessionFactory sessionFactory) {
        HibernateTransactionManager transactionManager = new HibernateTransactionManager();
        transactionManager.setSessionFactory(sessionFactory);
        return transactionManager;
    }

    private Properties hibernateProperties() {
        final Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", environment.getProperty("hibernate.dialect"));
        properties.setProperty("hibernate.connection.driver_class", environment.getProperty("hibernate.connection.driver_class"));
        properties.setProperty("hibernate.hbm2ddl.auto", environment.getProperty("hibernate.hbm2ddl.auto"));
        properties.setProperty("hibernate.globally_quoted_identifiers", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        properties.setProperty("hibernate.jdbc.batch_size", Dialect.DEFAULT_BATCH_SIZE);
        properties.setProperty("show_sql", environment.getProperty("spring.jpa.show-sql"));
        properties.setProperty("hibernate.connection.pool_size", Integer.toString((Integer) dbProperties.get("hibernate.connection.pool_size")));
        return properties;
    }
}
