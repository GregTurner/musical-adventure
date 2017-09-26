package ca.rom.mediaplayer;

import android.content.Context;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Observable;


/**
 * Access to the media lists, register your observer and it will send you a stream of updates
 * in order they are supposed to be played back in
 */
public class MediaDataService extends Observable {
    private static final String TAG = "MediaDataService";
    private static final int THREAD_POOL_SIZE = 12;

    private static MediaDataService mInstance;
    private RequestQueue mRequestQueue;
    private static Context mCtx;



    private ArrayList<MediaItem> mediaItemList;
    public int mediaItemIndex = 0;

    private MediaDataService(Context context) {
        mCtx = context;
        mRequestQueue = getRequestQueue();
    }

    /**
     * Singleton accessor
     * @param context
     * @return
     */
    public static synchronized MediaDataService getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MediaDataService(context);
        }
        return mInstance;
    }

    public void refreshMediaList()  {

        // Init ourselve as we could be called over and over againn forever
        mediaItemIndex = 0;

        // Setup URL
        String url = mCtx.getString(R.string.media_list_url);
        Log.d(TAG, "Starting getting media list from server: " + url);

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
            (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, "Get media list response received: " + response.toString());

                    // Despite the 200, we have to add sanity checks here
                    // Ensure the list has values
                    try {

                        // Parse response
                        JSONArray mediaItems = response.getJSONArray("media_items");

                        // Do some sanity checks
                        if (mediaItems == null || mediaItems.length() < 1) {
                            Log.w(TAG, "Media list is empty.  This is a known issue with API.");
                            refreshMediaList();
                            return;
                        }

                        // Init our list
                        mediaItemList = new ArrayList<>(mediaItems.length());

                        // Copy values from response
                        for (int i = 0; i < mediaItems.length(); i++) {
                            String mediaId = mediaItems.getString(i);
                            Log.d(TAG, "Adding media item: " + mediaId);
                            mediaItemList.add(new MediaItem(mediaId, i));
                        }

                        // Trigger the media item requests
                        fillMediaItemList();

                    } catch (JSONException e) {
                        // Based parsing, retry!
                        Log.w(TAG, "Media list response could not be parsed: " + e.getMessage());
                        refreshMediaList();
                    }
                }
            }, new Response.ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    // Bad response, retry!
                    Log.w(TAG, "Get media list error response received: " + error.getMessage());
                    refreshMediaList();
                }
            });

        // Add to queue
        this.addToRequestQueue(jsObjRequest);
    }

    /**
     * Fills in the details of the media list items using the HTTP Queue
     * starting in the playback order, however, the HTTP responses can be out of order
     */
    private void fillMediaItemList () {
        for (MediaItem item : mediaItemList) {
            this.fillMediaItem(item);
        }
    }

    /**
     * Fills in a single media item using the ID
     */
    private void fillMediaItem (final MediaItem mediaItem) {
        // Setup URL
        String url = mCtx.getString(R.string.media_list_url) + "/" + mediaItem.id;
        Log.d(TAG, "Starting getting a media item from server: " + url);

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, "Get media item response received: " + response.toString());

                        // Do some sanity checks
                        // Parse response
                        JSONArray subMediaItems;

                        try {
                            subMediaItems = response.getJSONArray("id");

                            // Sanity checks
                            if (subMediaItems == null || subMediaItems.length() < 1) {
                                Log.e(TAG, "Media item contains more than one item.  Skipping but should we retry?");
                                return;
                            }

                            // Now fill in the data
                            mediaItem.url = ((JSONObject)subMediaItems.get(0)).getString("url");
                            if (mediaItem.url == null || mediaItem.url.isEmpty()) {
                                Log.e(TAG, "Media item missing URL");
                                fillMediaItem(mediaItem);
                            }

                            Log.d(TAG, "Media item added: " + mediaItem.id);

                            onMediaItemFilled();

                        } catch (JSONException e) {
                            // Retry
                            Log.w(TAG, "Media item parsing exception: " + e.getMessage());
                            fillMediaItem(mediaItem);
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Bad response, retry!
                        Log.w(TAG, "Get media item error response received: " + error.getMessage());
                        fillMediaItem(mediaItem);
                    }
                });

        // Add to queue
        this.addToRequestQueue(jsObjRequest);
    }

    /**
     * One of the media item responses came back OK, determine if this is next in line for
     * observers
     */
    private void onMediaItemFilled () {

        // Out of bounds!
        if (mediaItemIndex >= getTotalMediaItemCount()) {
            Log.d(TAG, "At the end of media list." + mediaItemIndex);

            // reset index
            mediaItemIndex = 0;


            return;
        }

        MediaItem currentMediaItem = mediaItemList.get(mediaItemIndex);
        if (currentMediaItem == null ||
                currentMediaItem.url == null ||
                currentMediaItem.url.isEmpty()) {
            Log.d(TAG, "Current media item is null, waiting: " + mediaItemIndex);
            return;
        }

        // If we have a URL,  increment on current index
        // also, keep trying to fire in case things went out of order


        // Notify we're all done
        Log.d(TAG, "Notifying observers: " + mediaItemIndex);
        setChanged();
        notifyObservers(mediaItemList.get(mediaItemIndex));

        this.mediaItemIndex++;
        onMediaItemFilled();
    }

    /**
     * Returns number of media items, only call after initial observer update
     * @return number of media items
     */
    public int getTotalMediaItemCount () {
        return mediaItemList.size();
    }

    /**
     * Lazy initializes our Volley HTTP Queue with disk based caching
     * @return RequestQueue
     */
    private RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {

            Log.d(TAG, "Creating new HTTP queue");

            // Instantiate the cache
            Cache cache = new DiskBasedCache(mCtx.getApplicationContext().getCacheDir(), 1024 * 1024); // 1MB cap

            // Set up the network to use HttpURLConnection as the HTTP client.
            Network network = new BasicNetwork(new HurlStack());

            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            mRequestQueue = new RequestQueue(cache, network, THREAD_POOL_SIZE);

            // Start the queue
            mRequestQueue.start();

        }
        return mRequestQueue;
    }

    private <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

}
