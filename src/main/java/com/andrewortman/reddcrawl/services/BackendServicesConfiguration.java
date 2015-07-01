package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.ReddcrawlCommonConfiguration;
import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.RedditClientConfiguration;
import com.andrewortman.reddcrawl.repository.PersistenceConfiguration;
import com.andrewortman.reddcrawl.repository.StoryDao;
import com.andrewortman.reddcrawl.repository.SubredditDao;
import com.codahale.metrics.MetricRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import javax.annotation.Nonnull;
import java.util.List;

@Configuration
@Import({ReddcrawlCommonConfiguration.class, RedditClientConfiguration.class, PersistenceConfiguration.class})
public class BackendServicesConfiguration {

    @Autowired
    @Nonnull
    private Environment environment;

    @Autowired
    @Nonnull
    private RedditClient redditClient;

    @Autowired
    @Nonnull
    private StoryDao storyDao;

    @Autowired
    @Nonnull
    private SubredditDao subredditDao;

    @Autowired
    @Nonnull
    private MetricRegistry metricRegistry;

    @Bean
    public StoryHistoryUpdaterService storyHistoryUpdaterService() {
        return new StoryHistoryUpdaterService(redditClient,
                storyDao,
                environment.getRequiredProperty("service.storyhistoryupdater.workers", Integer.class),
                environment.getRequiredProperty("service.storyhistoryupdater.oldeststory", Integer.class),
                environment.getRequiredProperty("service.storyhistoryupdater.interval", Integer.class),
                metricRegistry);
    }

    @Bean
    public SubredditScraperService subredditScraperService() {
        return new SubredditScraperService(redditClient,
                subredditDao,
                metricRegistry);
    }

    @Bean
    public NewStoryScraperService storyScraperService() {
        return new NewStoryScraperService(redditClient,
                storyDao,
                subredditDao,
                environment.getRequiredProperty("service.newstoryscraper.storycount", Integer.class),
                metricRegistry);
    }

    @Bean
    public ServiceManager serviceManager(@Nonnull final List<Service> serviceList) {
        final ServiceManager serviceManager = new ServiceManager();
        for (final Service service : serviceList) {
            serviceManager.addService(service);
        }

        return serviceManager;
    }
}
