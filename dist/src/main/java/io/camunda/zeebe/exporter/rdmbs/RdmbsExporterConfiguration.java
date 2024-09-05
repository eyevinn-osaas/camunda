/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.rdmbs;

import io.camunda.exporter.rdbms.sql.MapperHolder;
import io.camunda.exporter.rdbms.sql.ProcessInstanceMapper;
import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RdmbsExporterConfiguration {

  @Bean
  public MultiTenantSpringLiquibase customerLiquibase(DataSource dataSource) {
    var moduleConfig = new MultiTenantSpringLiquibase();
    moduleConfig.setDataSource(dataSource);
    // changelog file located in src/main/resources directly in the module
    moduleConfig.setChangeLog("db/changelog/rdbms-exporter/changelog-master.xml");
    return moduleConfig;
  }

  @Bean
  public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);
    return factoryBean.getObject();
  }

  @Bean
  public MapperFactoryBean<ProcessInstanceMapper> processInstanceMapper(SqlSessionFactory sqlSessionFactory) throws Exception {
    MapperFactoryBean<ProcessInstanceMapper> factoryBean = new MapperFactoryBean<>(ProcessInstanceMapper.class);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  @Bean
  public Object something(ProcessInstanceMapper processInstanceMapper) {
    MapperHolder.PROCESS_INSTANCE_MAPPER = processInstanceMapper;
    return new Object();
  }
}
