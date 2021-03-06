package com.andrewortman.reddcrawl;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Strings;
import com.signalfx.codahale.reporter.SignalFxReporter;
import org.coursera.metrics.datadog.DatadogReporter;
import org.coursera.metrics.datadog.transport.HttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;

import javax.annotation.Nonnull;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Creates a spring Environment object and configures how Spring reads properties
 * Spring will read the classpath's application properties file first.
 * The default classpath property file will allow environment variables to override the defaults. Check the provided application properties for defaults
 * Any properties in a file application.properties located in the current working directory (if it exists) will provide a final override.
 */

@Configuration
@PropertySources({@PropertySource("classpath:application.properties"),
        @PropertySource(value = "file:application.properties", ignoreResourceNotFound = true)})
public class ReddcrawlCommonConfiguration {

    private final static Logger LOGGER = LoggerFactory.getLogger(ReddcrawlCommonConfiguration.class);

    @Nonnull
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyPlaceholder() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Nonnull
    @Bean
    public MetricRegistry metricsRegistry(@Nonnull final Environment environment) throws UnknownHostException {
        final MetricRegistry metricRegistry = new MetricRegistry();
        final String datadogAPIKey = environment.getProperty("metrics.datadog.apikey");
        final String signalFxAPIKey = environment.getProperty("metrics.signalfx.apikey");
        if (!Strings.isNullOrEmpty(datadogAPIKey)) {
            final HttpTransport httpTransport = new HttpTransport.Builder()
                    .withApiKey(datadogAPIKey).build();

            final DatadogReporter reporter = DatadogReporter.forRegistry(metricRegistry)
                    .withExpansions(EnumSet.of(DatadogReporter.Expansion.COUNT,
                            DatadogReporter.Expansion.MAX,
                            DatadogReporter.Expansion.MIN,
                            DatadogReporter.Expansion.MEDIAN,
                            DatadogReporter.Expansion.MEAN,
                            DatadogReporter.Expansion.P95,
                            DatadogReporter.Expansion.P99,
                            DatadogReporter.Expansion.RATE_5_MINUTE,
                            DatadogReporter.Expansion.RATE_1_MINUTE))
                    .withHost(InetAddress.getLocalHost().getHostName())
                    .withTags(Collections.singletonList("reddcrawl"))
                    .withTransport(httpTransport)
                    .build();

            final int intervalSeconds = environment.getProperty("metrics.datadog.interval", Integer.class, 10);
            reporter.start(intervalSeconds, TimeUnit.SECONDS);

            LOGGER.info("Started datadog logging - emitting events every " + intervalSeconds + " seconds");
        } else {
            LOGGER.info("Datadog logging omitted - no api key set in configuration");
        }

        if (!Strings.isNullOrEmpty(signalFxAPIKey)) {
            final SignalFxReporter signalFxReporter = new SignalFxReporter.Builder(metricRegistry, signalFxAPIKey).build();
            final int intervalSeconds = environment.getProperty("metrics.signalfx.interval", Integer.class, 5);
            signalFxReporter.start(intervalSeconds, TimeUnit.SECONDS);

            LOGGER.info("Started SignalFX logging - emitting events every " + intervalSeconds + " seconds");
        } else {
            LOGGER.info("SignalFX logging omitted - no api key set in configuration");
        }

        return metricRegistry;
    }

}
