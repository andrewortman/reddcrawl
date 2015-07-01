package com.andrewortman.reddcrawl.services;

/**
 * Guava's service manager didn't have a nice way of making super resilent services, so I wrote my own Service class
 * This abstract class allows interrupting (clean shutdown), defining a "runIteration" that can be used to run
 * a long loop or a single iteration, and finally an ability to define a minimum repetition time to limit that max
 * iterations over a single period.
 */
public abstract class Service {
    //stores interrupted state
    private boolean isInterrupted = false;

    /**
     * Put your code in here to run. Exceptions are caught and logged automatically
     *
     * @throws Exception
     */
    public abstract void runIteration() throws Exception;

    /**
     * Minimum amount of time between two consecutive runIteration runs. If a service stops before the next iteration, the thread
     * it was running in will sleep until the next scheduled time
     *
     * @return number of seconds between iterations
     */
    public abstract int getMinimumRepetitionTimeInSeconds();

    /**
     * Time to wait between executions if there was an exception emitted. Do not override if you want exceptions to be treated
     * just like a succesfull completion
     */
    public int getRepeatDelayInSecondsIfExceptionOccurred() {
        return -1;
    }

    /**
     * Interrupt this service
     */
    public void interrupt() {
        this.isInterrupted = true;
    }

    /**
     * Used internally to stop any loops for a clean shutdown
     */
    public boolean interrupted() {
        return this.isInterrupted;
    }
}
