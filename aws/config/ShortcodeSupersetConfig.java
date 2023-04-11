package com.freecharge.smsprofilerservice.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@Configuration
@PropertySource(value="classpath:config/shortcode.properties")
public class ShortcodeSupersetConfig {

    @Value("#{'${shortcodes.superset.list}'.split(',')}")
    private List<String> shortcodes;

    @Bean
    public List<String> getShortcodeBean(){
        return shortcodes;
    }


}
