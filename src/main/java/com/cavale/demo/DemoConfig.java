package com.cavale.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wires the demo feature's configuration properties. */
@Configuration
@EnableConfigurationProperties(DemoProperties.class)
public class DemoConfig {
}
