package net.kazyx.wirespider.android.testapp;

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

    public boolean isUnlockedByFailure() {
        return isUnlockedByFailure;
    }
}
