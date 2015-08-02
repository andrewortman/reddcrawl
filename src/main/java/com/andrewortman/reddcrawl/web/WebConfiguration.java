package com.andrewortman.reddcrawl.web;

import com.andrewortman.reddcrawl.ReddcrawlCommonConfiguration;
import com.andrewortman.reddcrawl.repository.PersistenceConfiguration;
import com.andrewortman.reddcrawl.repository.json.StoryJsonBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.annotation.Nonnull;

@Configuration
@ComponentScan("com.andrewortman.reddcrawl.web")
@EnableWebMvc
@Import({ReddcrawlCommonConfiguration.class, PersistenceConfiguration.class})
public class WebConfiguration {
    @Nonnull
    @Bean
    public StoryJsonBuilder storyJsonService() {
        return new StoryJsonBuilder();
    }
}