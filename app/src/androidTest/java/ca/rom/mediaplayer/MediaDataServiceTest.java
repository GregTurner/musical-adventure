package ca.rom.mediaplayer;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class MediaDataServiceTest {
    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("ca.rom.mediaplayer", appContext.getPackageName());
    }

    @Test
    public void ConstructMediaDataSingleton() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        // Init singleton
        MediaDataService mediaData = MediaDataService.getInstance(appContext);
        MockObserver mockObserver = new MockObserver();
        mediaData.addObserver(mockObserver);

        // Kick of the service
        mediaData.refreshMediaList();
        mockObserver.waitUntilUpdateIsCalled();

        // Ensure indexes are in sync with the mock observer
        for (int i = 0; i < mediaData.getTotalMediaItemCount(); i++) {
            // check out queue is insync
            MediaItem mediaItem = mockObserver.queue.remove();
            assertEquals(mediaItem.sequenceNumber, i);
            mockObserver.waitUntilUpdateIsCalled();
        }

    }
}

