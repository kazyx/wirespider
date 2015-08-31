/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.sampleapp;

import android.app.Application;
import android.util.Log;

import net.kazyx.wirespider.WsLog;

public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        WsLog.writer(new WsLog.Writer() {
            @Override
            public void v(String tag, String message) {
                Log.v(tag, message);
            }

            @Override
            public void d(String tag, String message) {
                Log.d(tag, message);
            }

            @Override
            public void e(String tag, String message) {
                Log.e(tag, message);
            }

            @Override
            public void printStackTrace(String tag, Throwable th) {
                Log.wtf(tag, th);
            }
        });
        WsLog.logLevel(WsLog.Level.DEBUG);
    }
}
