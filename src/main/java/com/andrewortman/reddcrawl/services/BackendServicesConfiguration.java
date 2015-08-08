package com.andrewortman.reddcrawl.services;

import com.andrewortman.reddcrawl.ReddcrawlCommonConfiguration;
import com.andrewortman.reddcrawl.archive.FileJsonArchive;
import com.andrewortman.reddcrawl.archive.GoogleStorageJsonArchive;
import com.andrewortman.reddcrawl.archive.JsonArchive;
import com.andrewortman.reddcrawl.client.RedditClient;
import com.andrewortman.reddcrawl.client.RedditClientConfiguration;
import com.andrewortman.reddcrawl.repository.PersistenceConfiguration;
import com.andrewortman.reddcrawl.repository.StoryRepository;
import com.andrewortman.reddcrawl.repository.SubredditRepository;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;

@SuppressWarnings("NullableProblems")
@Configuration
@Import({ReddcrawlCommonConfiguration.class, RedditClientConfiguration.class, PersistenceConfiguration.class})
public class BackendServicesConfiguration {

    @Nonnull
    private static final Logger LOGGER = LoggerFactory.getLogger(BackendServicesConfiguration.class);

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

    @Nonnull
    @Bean
    public StoryHistoryUpdaterService storyHistoryUpdaterService() {
        return new StoryHistoryUpdaterService(redditClient,
                storyRepository,
                environment.getRequiredProperty("service.storyhistoryupdater.workers", Integer.class),
                environment.getRequiredProperty("service.storyhistoryupdater.oldeststory", Integer.class),
                environment.getRequiredProperty("service.storyhistoryupdater.interval", Integer.class),
                metricRegistry);
    }

    @Nonnull
    @Bean
    public NewSubredditScraperService newSubredditScraperService() {
        return new NewSubredditScraperService(redditClient,
                subredditRepository,
                metricRegistry);
    }

    @Nonnull
    @Bean
    public SubredditHistoryUpdaterService subredditHistoryUpdaterService() {
        return new SubredditHistoryUpdaterService(redditClient,
                subredditRepository,
                metricRegistry,
                environment.getRequiredProperty("service.subreddithistoryupdater.interval", Integer.class));
    }

    @Nonnull
    @Bean
    public NewStoryScraperService storyScraperService() {
        return new NewStoryScraperService(redditClient,
                storyRepository,
                subredditRepository,
                environment.getRequiredProperty("service.newstoryscraper.newstorycount", Integer.class),
                environment.getRequiredProperty("service.newstoryscraper.hotstorycount", Integer.class),
                environment.getRequiredProperty("service.newstoryscraper.subredditexpirationinterval", Integer.class),
                environment.getRequiredProperty("service.newstoryscraper.interval", Integer.class),
                metricRegistry);
    }

    @Nonnull
    @Bean
    public ServiceManager serviceManager(@Nonnull final List<Service> serviceList) {
        final ServiceManager serviceManager = new ServiceManager(metricRegistry);

        for (final Service service : serviceList) {
            serviceManager.addService(service);
        }

        return serviceManager;
    }

    @Nonnull
    @Bean
    public JsonArchive jsonArchive() {
        try {
            final GoogleStorageJsonArchive googleStorageJsonArchive =
                    new GoogleStorageJsonArchive(environment.getRequiredProperty("service.archive.google.bucket", String.class));

            LOGGER.info("Using GoogleStorageJsonArchive!");
            return googleStorageJsonArchive;
        } catch (final Exception e) {
            LOGGER.error("Could not use Google Storage Json Archive - check to make sure that GOOGLE_APPLICATION_CREDENTIALS is set!", e);
            LOGGER.info("Falling back to FileJsonArchive instead");
            return new FileJsonArchive(new File(environment.getRequiredProperty("service.archive.file.directory", String.class)));
        }
    }

    @Nonnull
    @Bean
    public StoryArchivingService storyArchivingService() {
        return new StoryArchivingService(storyRepository,
                environment.getRequiredProperty("service.archive.oldeststory", Integer.class),
                environment.getRequiredProperty("service.archive.batchinterval", Integer.class),
                environment.getRequiredProperty("service.archive.maxbatchsize", Integer.class),
                metricRegistry, jsonArchive());
    }
}
