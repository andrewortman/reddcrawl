package com.andrewortman.reddcrawl.repository;

import com.andrewortman.reddcrawl.ReddcrawlCommonConfiguration;
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Spring config for all persistence related components
 */
@Configuration
@EnableTransactionManagement
@ComponentScan("com.andrewortman.reddcrawl.repository")
@Import(ReddcrawlCommonConfiguration.class)
public class PersistenceConfiguration {

    @Autowired
    private Environment env;

    @Bean
    public DataSource pgDataSource() throws SQLException {
        final BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(env.getProperty("db.driver"));
        ds.setUrl(env.getRequiredProperty("db.url"));
        ds.setUsername(env.getRequiredProperty("db.username"));
        ds.setPassword(env.getRequiredProperty("db.password"));
        ds.setInitialSize(env.getProperty("db.initialSize", Integer.class, 8));
        ds.setMaxTotal(env.getProperty("db.maxSize", Integer.class, 48));
        ds.setMaxIdle(env.getProperty("db.minIdle", Integer.class, 4));
        return ds;
    }

    @Bean
    public EntityManagerFactory entityManagerFactory() throws SQLException {
        final LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaDialect(new HibernateJpaDialect());
        factory.setDataSource(pgDataSource());
        factory.setPackagesToScan(this.getClass().getPackage().getName());
        factory.setJpaProperties(this.additionalProperties());
        factory.afterPropertiesSet();

        return factory.getObject();
    }

    @Bean
    PlatformTransactionManager transactionManager() throws SQLException {
        final JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory());
        return transactionManager;
    }

    private Properties additionalProperties() {
        final Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", env.getProperty("db.hibernate.dialect"));
        properties.setProperty("hibernate.hbm2ddl.auto", env.getProperty("db.hibernate.hbm2ddl.auto"));
        properties.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        return properties;
    }
}
