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

import net.kazyx.wirespider.sampleapp.echoserver.LocalServerManager;

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

        mLocalServerManager = new LocalServerManager(10000);

        getFragmentManager().beginTransaction()
                .replace(R.id.contentRoot, FirstFragment.newInstance())
                .commit();
    }

    @Override
    protected void onDestroy() {
        mManager.dispose();
        mLocalServerManager.shutdownAsync();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
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
        getFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.contentRoot, SecondFragment.newInstance())
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDisconnected() {
        int count = getFragmentManager().getBackStackEntryCount();
        if (count != 0) {
            getFragmentManager().popBackStack();
        }
    }

    private LocalServerManager mLocalServerManager;

    @Override
    public LocalServerManager getLocalServerManager() {
        return mLocalServerManager;
    }
}
