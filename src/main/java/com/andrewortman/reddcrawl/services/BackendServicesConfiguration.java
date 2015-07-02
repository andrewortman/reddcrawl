package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.ReddcrawlCommonConfiguration;
import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.RedditClientConfiguration;
import com.andrewortman.reddcrawl.repository.PersistenceConfiguration;
import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.repository.SubredditRepository;
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
    private StoryRepository storyRepository;

    @Autowired
    @Nonnull
    private SubredditRepository subredditRepository;

    @Autowired
    @Nonnull
    private MetricRegistry metricRegistry;

    @Bean
    public StoryHistoryUpdaterService storyHistoryUpdaterService() {
        return new StoryHistoryUpdaterService(redditClient,
                storyRepository,
                environment.getRequiredProperty("service.storyhistoryupdater.workers", Integer.class),
                environment.getRequiredProperty("service.storyhistoryupdater.oldeststory", Integer.class),
                environment.getRequiredProperty("service.storyhistoryupdater.interval", Integer.class),
                metricRegistry);
    }

    @Bean
    public NewSubredditScraperService newSubredditScraperService() {
        return new NewSubredditScraperService(redditClient,
                subredditRepository,
                metricRegistry);
    }

    @Bean
    public SubredditHistoryUpdaterService subredditHistoryUpdaterService() {
        return new SubredditHistoryUpdaterService(redditClient,
                subredditRepository,
                metricRegistry);
    }

    @Bean
    public NewStoryScraperService storyScraperService() {
        return new NewStoryScraperService(redditClient,
                storyRepository,
                subredditRepository,
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
