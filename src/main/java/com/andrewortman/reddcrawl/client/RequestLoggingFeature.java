package com.andrewortman.reddcrawl.client;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * reddcrawl
 * com.andrewortman.reddcrawl.client
 *
 * @author andrewo
 */
public class RequestLoggingFeature implements Feature {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFeature.class);

    @Nonnull
    private final MetricRegistry metricRegistry;

    public RequestLoggingFeature(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public boolean configure(FeatureContext context) {

        final Meter clientRequestMeter =
                metricRegistry.meter(MetricRegistry.name("reddcrawl", "client", "requests"));

        final Timer clientRequestTimer =
                metricRegistry.timer(MetricRegistry.name("reddcrawl", "client", "requests", "time"));


        context
                .register(new ClientRequestFilter() {
                    @Override
                    public void filter(final ClientRequestContext requestContext) throws IOException {
                        requestContext.setProperty("start_time", new Date());
                        clientRequestMeter.mark();
                        LOGGER.debug("Making request to " + requestContext.getUri());
                    }
                })
                .register(new ClientResponseFilter() {
                    @Override
                    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
                        final Object startTimeObj = requestContext.getProperty("start_time");
                        if (startTimeObj == null || !(startTimeObj instanceof Date)) {
                            return;
                        }

                        final Date startTime = (Date) startTimeObj;

                        final long timeMillis = new Date().getTime() - startTime.getTime();
                        clientRequestTimer.update(timeMillis, TimeUnit.MILLISECONDS);
                    }
                });

        return true;
    }
}
