package com.chatapp.config;

import com.chatapp.common.aop.SqlTraceQueryListener;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

/**
 * Wraps the auto-configured DataSource with datasource-proxy via BeanPostProcessor. Using
 * BeanPostProcessor avoids the circular dependency that occurs when injecting DataSource directly
 * as a @Bean parameter (Flyway -> JPA -> DataSource cycle).
 */
@Configuration
@ConditionalOnProperty(name = "app.sql-trace.enabled", havingValue = "true")
public class DataSourceProxyConfig implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
    if (bean instanceof DataSource ds && !(bean instanceof ProxyDataSource)) {
      return ProxyDataSourceBuilder.create(ds)
          .name("sql-trace-proxy")
          .listener(new SqlTraceQueryListener())
          .build();
    }
    return bean;
  }
}
