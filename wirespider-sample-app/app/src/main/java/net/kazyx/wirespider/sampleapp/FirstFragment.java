/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.sampleapp;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import net.kazyx.wirespider.CloseStatusCode;
import net.kazyx.wirespider.WebSocket;
import net.kazyx.wirespider.sampleapp.echoserver.LocalServerManager;

import java.net.URI;
import java.net.URISyntaxException;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;

public class FirstFragment extends Fragment {
    private static final String TAG = FirstFragment.class.getSimpleName();

    public static FirstFragment newInstance() {
        return new FirstFragment();
    }

    @Bind(R.id.url_edit_box)
    EditText mUrlEdit;

    @Bind(R.id.launch_server_switch)
    Switch mServerSwitch;

    @Bind(R.id.port_indicator)
    TextView mLocalServerPortText;

    private ActivityProxy mActivityProxy;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            init((ActivityProxy) activity);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            init((ActivityProxy) context);
        }
    }

    private void init(ActivityProxy activityProxy) {
        mActivityProxy = activityProxy;
    }

    @Override
    public void onDetach() {
        mActivityProxy = null;
        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View v = inflater.inflate(R.layout.fragment_first, container, false);
        ButterKnife.bind(this, v);
        mServerSwitch.setEnabled(true);
        mServerSwitch.setChecked(mActivityProxy.getLocalServerManager().isRunning());

        WebSocket ws = mActivityProxy.getClientManager().getWebSocket();
        if (ws != null) {
            ws.closeAsync(CloseStatusCode.NORMAL_CLOSURE, "normal closure");
        }
        mViewWidth = container.getWidth();
        return v;
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    @OnCheckedChanged(R.id.launch_server_switch)
    void onCheckedChanged(final Switch sw, boolean isChecked) {
        if (isChecked) {
            if (!mActivityProxy.getLocalServerManager().isRunning()) {
                sw.setEnabled(false);
                mActivityProxy.getLocalServerManager().bootAsync(new LocalServerManager.ServerLifeCycle() {
                    @Override
                    public void onLaunched() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onServerLaunched();
                            }
                        });
                    }

                    @Override
                    public void onStopped() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onServerStopped();
                            }
                        });
                    }
                });
            } else {
                onServerLaunched();
            }
        } else if (mActivityProxy.getLocalServerManager().isRunning()) {
            mActivityProxy.getLocalServerManager().shutdownAsync();
            onServerStopped();
        }
    }

    private void onServerLaunched() {
        if (isVisible()) {
            mServerSwitch.setEnabled(true);
            mServerSwitch.setChecked(true);
            mLocalServerPortText.setText(String.format(getString(R.string.listening), "10000"));
            mLocalServerPortText.setVisibility(View.VISIBLE);
        }
    }

    private void onServerStopped() {
        if (isVisible()) {
            mServerSwitch.setEnabled(true);
            mLocalServerPortText.setVisibility(View.INVISIBLE);
        }
    }

    @OnClick(R.id.connect_button)
    void onClick(Button b) {
        String text = mUrlEdit.getText().toString();
        if (text.length() == 0) {
            text = getString(R.string.url_hint);
        }
        try {
            URI uri = new URI(text);
            mActivityProxy.getClientManager().open(uri, new ClientManager.ConnectionListener() {
                @Override
                public void onConnected() {
                    Log.w(TAG, "Connected");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mActivityProxy != null) {
                                mActivityProxy.onConnected();
                            }
                        }
                    });
                }

                @Override
                public void onConnectionFailed(final Exception e) {
                    Log.w(TAG, "Connection Failed");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isVisible()) {
                                return;
                            }
                            Toast.makeText(getActivity(), "Connection Failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (URISyntaxException e) {
            Log.w(TAG, "URISyntaxException");
            Toast.makeText(getActivity(), text + " is invalid URI", Toast.LENGTH_SHORT).show();
        }
    }

    private void runOnUiThread(Runnable task) {
        new Handler(Looper.getMainLooper()).post(task);
    }

    private int mViewWidth;

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        if (transit == FragmentTransaction.TRANSIT_FRAGMENT_CLOSE && enter) {
            Animator anim = ObjectAnimator.ofFloat(getView(), "x", -mViewWidth, 0.0f);
            anim.setDuration(700).setInterpolator(new OvershootInterpolator());
            return anim;
        } else {
            return super.onCreateAnimator(transit, enter, nextAnim);
        }
    }
}
