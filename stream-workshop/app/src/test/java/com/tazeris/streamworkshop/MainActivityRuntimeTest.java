package com.tazeris.streamworkshop;

import static org.junit.Assert.*;
import android.app.Activity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityRuntimeTest {
    @Test
    public void activityStartsAndStaysAlive() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(activity);
        assertFalse(activity.isFinishing());
    }
}
