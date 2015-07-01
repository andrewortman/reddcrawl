package com.andrewortman.reddcrawl.web;

import com.andrewortman.reddcrawl.ReddcrawlCommonConfiguration;
import com.andrewortman.reddcrawl.repository.PersistenceConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@ComponentScan("com.andrewortman.reddcrawl.web")
@EnableWebMvc
@Import({ReddcrawlCommonConfiguration.class, PersistenceConfiguration.class})
public class WebConfiguration {

}