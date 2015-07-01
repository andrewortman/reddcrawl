import com.andrewortman.reddcrawl.client.ratelimiting.TokenBucketRateLimiter;
import com.codahale.metrics.MetricRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter limiter;

    @Before
    public void setUp() {
        limiter = new TokenBucketRateLimiter(600, new MetricRegistry());
    }

    private void emptyFullBucket() {
        for (int i = 0; i < 600; i++) {
            assertEquals(0, (long) limiter.getAmountOfTimeToSleep());
        }
    }

    @Test
    public void testStandardDecrement() throws InterruptedException {
        emptyFullBucket();
        assertEquals(100, (long) limiter.getAmountOfTimeToSleep());
        assertEquals(200, (long) limiter.getAmountOfTimeToSleep());
        assertEquals(300, (long) limiter.getAmountOfTimeToSleep());
        Thread.sleep(300);
        assertEquals(100, (long) limiter.getAmountOfTimeToSleep());
    }

    @Test
    public void testLimitedBucketSize() throws InterruptedException {
        emptyFullBucket();
        assertEquals(100, (long) limiter.getAmountOfTimeToSleep());
        Thread.sleep(100);
        Thread.sleep(60000);
        for (int i = 0; i < 600; i++) {
            assertEquals(0, (long) limiter.getAmountOfTimeToSleep());
        }

        assertEquals(100, (long) limiter.getAmountOfTimeToSleep());
    }
}
