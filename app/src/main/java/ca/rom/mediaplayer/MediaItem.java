package ca.rom.mediaplayer;

/**
 * Created by gregf on 9/25/2017.
 */

import org.json.JSONException;
import org.json.JSONObject;

/**
 * POJO For a media item
 */
public class MediaItem {
    /**
     * Indicates what order the media item is in.  Only used to double check the order is
     * correct.
     */
    public int sequenceNumber;

    /**
     * A unique identifier for this MediaItem, i.e., GUID as string
     */
    public String id;

    /**
     * The URL to the media resource
     */
    public String url;

    /**
     * Constructor
     * @param id
     * @param sequenceNumber
     */
    public MediaItem(String id, int sequenceNumber) {
        this.id = id;
        this.sequenceNumber = sequenceNumber;
    }
}

/*

This class modelled off of this sample JSON response:


{
    "id": [
        {
            "quality": "auto",
            "duration": 5,
            "name": "media-item-1",
            "url": "https://tungsten.aaplimg.com/VOD/bipbop_adv_example_v2/master.m3u8",
            "id": "dde6215d-14bb-4b37-bb01-68287228615a"
        }
    ]
}

 */
