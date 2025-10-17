package com.gnemirko.movieRecsBot.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.Semaphore;

@Component
public class LlmBulkhead {
    private final Semaphore permits = new Semaphore(10);

    public void acquire() { permits.acquireUninterruptibly(); }
    public void release() { permits.release(); }
}