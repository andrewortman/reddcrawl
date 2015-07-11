package com.andrewortman.reddcrawl.services;

import com.codahale.metrics.MetricRegistry;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Guava's service manager didn't have a nice way of making super resilent services, so I wrote my own Service class
 * This abstract class allows interrupting (clean shutdown), defining a "runIteration" that can be used to run
 * a long loop or a single iteration, and finally an ability to define a minimum repetition time to limit that max
 * iterations over a single period.
 */
public class ServiceManager {
    @Nonnull
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceManager.class);
    @Nonnull
    private final Map<Service, Thread> serviceThreadMap = new HashMap<>();
    @Nonnull
    private final MetricRegistry metricRegistry;

    public ServiceManager(@Nonnull final MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        //set up shutdown hook for clean shutdowns
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("Shutting down services");
                synchronized (serviceThreadMap) {
                    for (final Service service : serviceThreadMap.keySet()) {
                        LOGGER.info("Interrupting " + service.getClass().getName());
                        service.interrupt();
                        serviceThreadMap.get(service).interrupt();
                    }
                }
            }
        }));
    }

    /**
     * Adds a service to the service manager
     *
     * @param service Service to manage
     */
    public void addService(@Nonnull final Service service) {
        final Thread thread = new Thread(new Runnable() {
            @Nonnull
            private DateTime lastRunTime = DateTime.now();

            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    lastRunTime = DateTime.now();
                    boolean exceptionOccurred = false;
                    try {
                        LOGGER.info("Starting service '" + service.getClass().getName() + "'");
                        metricRegistry.counter(MetricRegistry.name("reddcrawl", "services", service.getClass().getSimpleName(), "runs")).inc();

                        //run the iteration and time it
                        final long startTime = new Date().getTime();
                        service.runIteration();
                        final long endTime = new Date().getTime();
                        metricRegistry.timer(MetricRegistry.name("reddcrawl", "service", service.getClass().getSimpleName(), "time")).update((endTime - startTime), TimeUnit.MILLISECONDS);
                    } catch (@Nonnull final Exception e) {
                        //handle the exception for a service
                        exceptionOccurred = true;
                        LoggerFactory.getLogger(service.getClass()).error("ServiceManager caught exception: " + e.getClass().getName() + " - " + e.getMessage(), e);
                        metricRegistry.counter(MetricRegistry.name("reddcrawl", "services", service.getClass().getSimpleName(), "exceptions")).inc();
                    }

                    final DateTime nextStartTime;
                    if (exceptionOccurred && service.getRepeatDelayInSecondsIfExceptionOccurred() > 0) {
                        nextStartTime = DateTime.now().plusSeconds(service.getRepeatDelayInSecondsIfExceptionOccurred());
                        LOGGER.info("An exception occurred for " + service.getClass().getName() + " and it's repeat delay" +
                                " was set.. going to schedule repetition for " + service.getRepeatDelayInSecondsIfExceptionOccurred() + " seconds from now");
                    } else {
                        nextStartTime = lastRunTime.plusSeconds(service.getMinimumRepetitionTimeInSeconds());
                    }

                    if (nextStartTime.isAfter(DateTime.now())) {
                        LOGGER.info("Service '" + service.getClass().getName() + "' not scheduled to run until " + nextStartTime + " - waiting until then");
                        while (nextStartTime.isAfter(DateTime.now())) {
                            try {
                                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                            } catch (@Nonnull final InterruptedException ignored) {
                                LOGGER.info("Received InterruptedException - bailing during sleep");
                            }
                        }
                    }
                }
            }
        });

        serviceThreadMap.put(service, thread);
    }

    /**
     * Kicks off all the threads to start
     */
    public void startAllThreads() {
        for (final Thread thread : serviceThreadMap.values()) {
            thread.start();
        }
    }

    /**
     * Joins all threads (blocks until all threads have exited compeltely
     */
    public void joinAllThreads() throws InterruptedException {
        for (final Thread thread : serviceThreadMap.values()) {
            thread.join();
        }
    }

}