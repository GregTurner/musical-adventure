package ca.rom.mediaplayer;

import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends AppCompatActivity implements Observer, Player.EventListener {
    private static final String TAG = "MediaPlayerMainActivity";

    private Handler mainHandler;
    private DataSource.Factory mediaDataSourceFactory;

    private SimpleExoPlayer player;

    private ConcurrentLinkedQueue<MediaSource> queue = new ConcurrentLinkedQueue<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "Starting onCreate.");

        super.onCreate(savedInstanceState);

        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Set content view
        setContentView(R.layout.activity_main);

        // Create new main handler and mediadatasourcefactory
        mainHandler = new Handler();
        mediaDataSourceFactory = buildDataSourceFactory();

        // 1. Create a default TrackSelector
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);

        // 2. Create the player
        player = ExoPlayerFactory.newSimpleInstance(getApplicationContext(), trackSelector);
        player.addListener(this);
        player.setPlayWhenReady(true);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);

        // Bind the player to the view.
        SimpleExoPlayerView simpleExoPlayerView = findViewById(R.id.player_view);
        simpleExoPlayerView.setPlayer(player);

        // Hide controls as it transitions to new media
        simpleExoPlayerView.setControllerAutoShow(false);

        // Add ourselves as a listener and kick off service
        Log.d(TAG, "Registering observer on MediaDataService");
        MediaDataService mediaDataService = MediaDataService.getInstance(getApplicationContext());
        mediaDataService.addObserver(this);
        mediaDataService.refreshMediaList();
    }

    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri)
                : Util.inferContentType("." + overrideExtension);
        switch (type) {
            case C.TYPE_SS:
                return new SsMediaSource(uri, buildDataSourceFactory(),
                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
            case C.TYPE_DASH:
                return new DashMediaSource(uri, buildDataSourceFactory(),
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
            case C.TYPE_HLS:
                return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, null);
            case C.TYPE_OTHER:
                return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                        mainHandler, null);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    /**
     * Returns a new DataSource factory.
     *
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory() {
        return new DefaultHttpDataSourceFactory("ROMMediaPlayer");
    }

    /**
     * The media service will call us when it has a new video url ready
     * @param observable MediaDataService that triggered the update
     * @param o MediaItem that is next to play
     */
    @Override
    public void update(Observable observable, Object o) {
        Log.d(TAG, "Update received from MediaDataService.");

        if (observable instanceof MediaDataService) {
            MediaItem nextMediaItem = (MediaItem) o;
            try {
                addMediaToQueue(nextMediaItem);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return;
        }
        Log.e(TAG, "Unknown observable type: " + observable.getClass());
    }

    /**
     * Queues next video to our media player
     * @param nextMediaItem The MediaItem to convert to mediasource and queue
     */
    public void addMediaToQueue(MediaItem nextMediaItem) throws InterruptedException {
        Log.d(TAG, "Adding media list to player");

        // Create the media
        MediaSource mediaSource = this.buildMediaSource(Uri.parse(nextMediaItem.url), null);

        // Queue it, we could have many threads in here so the queue is synchronized
        queue.add(mediaSource);

        // If first one kick off player
        play();
    }

    /**
     * Help determine if we're idle, ready, or ended to play the next video
     * @throws InterruptedException Throws it, but not sure in which condition
     */
    public void play() throws InterruptedException {

        int state = player.getPlaybackState();
        Log.d(TAG, "Playing if ready, current state: " + state);

        if (state == Player.STATE_IDLE ||
                state == Player.STATE_ENDED) {

            // Take next off queue
            if (queue.isEmpty()) {
                // Maybe we should get more!
                MediaDataService.getInstance(this).refreshMediaList();
            }

            MediaSource mediaSource = queue.remove();

            // Give the player the next media source
            Log.d(TAG, "Playing: " + mediaSource.toString());
            player.prepare(mediaSource);
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "Playback changed, current state: " + playbackState);

        if (player.getPlaybackState() == Player.STATE_ENDED) {
            Log.d(TAG, "Video finished");
            try {
                play();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    @Override
    public void onPositionDiscontinuity() {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }
}
