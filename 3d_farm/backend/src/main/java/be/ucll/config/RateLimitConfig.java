package be.ucll.config;

import be.ucll.config.LoginRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter() {
        FilterRegistrationBean<LoginRateLimitFilter> registrationBean = new FilterRegistrationBean<>();
        
        registrationBean.setFilter(new LoginRateLimitFilter());
        registrationBean.addUrlPatterns("/api/auth/login");
        registrationBean.setOrder(1);
        
        return registrationBean;
    }
}