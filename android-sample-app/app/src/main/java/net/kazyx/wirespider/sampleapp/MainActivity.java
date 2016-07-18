/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.sampleapp;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements ActivityProxy {

    private ClientManager mManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setTitle(R.string.app_name);

        try {
            mManager = new ClientManager();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        try {
            ProviderInstaller.installIfNeeded(SampleApp.getAppContext()); // For Android 4.4 and lower
        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
            Toast.makeText(getApplicationContext(), R.string.play_service_error, Toast.LENGTH_LONG).show();
        }

        getFragmentManager().beginTransaction()
                .replace(R.id.contentRoot, FirstFragment.newInstance())
                .commit();
    }

    @Override
    protected void onDestroy() {
        mManager.dispose();
        super.onDestroy();
    }

    private boolean mIsStarted = false;

    @Override
    public void onStart() {
        super.onStart();
        mIsStarted = true;
    }

    @Override
    public void onStop() {
        mIsStarted = false;
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        int count = getFragmentManager().getBackStackEntryCount();
        if (count != 0) {
            getFragmentManager().popBackStack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public ClientManager getClientManager() {
        return mManager;
    }

    @Override
    public void onConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!mIsStarted) {
                    return;
                }
                getFragmentManager().beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .replace(R.id.contentRoot, SecondFragment.newInstance())
                        .addToBackStack(null)
                        .commit();
            }
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!mIsStarted) {
                    return;
                }
                int count = getFragmentManager().getBackStackEntryCount();
                if (count != 0) {
                    getFragmentManager().popBackStack();
                }
            }
        });
    }
}
