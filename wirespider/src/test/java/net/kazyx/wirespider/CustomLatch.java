/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CustomLatch {
    private final CountDownLatch mLatch;
    private boolean isUnlockedByFailure;

    public CustomLatch(int count) {
        mLatch = new CountDownLatch(count);
    }

    public synchronized void countDown() {
        mLatch.countDown();
    }

    public synchronized void unlockByFailure() {
        if (mLatch.getCount() > 0) {
            isUnlockedByFailure = true;
            while (mLatch.getCount() > 0) {
                mLatch.countDown();
            }
        }
    }

    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        return mLatch.await(time, unit);
    }

    public synchronized long getCount() {
        return mLatch.getCount();
    }

    public synchronized boolean isUnlockedByCountDown() {
        return mLatch.getCount() == 0 && !isUnlockedByFailure;
    }

    public synchronized boolean isUnlockedByFailure() {
        return isUnlockedByFailure;
    }
}
