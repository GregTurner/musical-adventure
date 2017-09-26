package ca.rom.mediaplayer;

import android.util.Log;

import com.google.android.exoplayer2.source.MediaSource;

import java.lang.reflect.Field;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;

public class MockObserver implements Observer {

    private CountDownLatch latch;

    public ConcurrentLinkedQueue<MediaItem> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void update(Observable observable, Object o) {
        if (observable instanceof MediaDataService) {
            MediaDataService service = (MediaDataService)observable;
            queue.add((MediaItem) o);
            latch.countDown();
        }
    }

    /**
     * Used to wait until all observers have been notified, handy for unit testing
     * @throws InterruptedException
     */
    public void waitUntilUpdateIsCalled() throws InterruptedException {
        Log.d("TEST", "Waiting until called for next media URL.");
        latch = new CountDownLatch(1);
        latch.await();
    }
}
