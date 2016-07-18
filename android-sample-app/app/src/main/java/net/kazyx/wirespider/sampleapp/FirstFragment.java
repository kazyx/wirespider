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
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import net.kazyx.wirespider.CloseStatusCode;
import net.kazyx.wirespider.WebSocket;
import net.kazyx.wirespider.sampleapp.databinding.FirstFragmentBinding;

import java.net.URI;
import java.net.URISyntaxException;

public class FirstFragment extends Fragment {
    private static final String TAG = FirstFragment.class.getSimpleName();

    public static FirstFragment newInstance() {
        return new FirstFragment();
    }

    private FirstFragmentBinding mBinding;

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
        View v = inflater.inflate(R.layout.first_fragment, container, false);
        mBinding = DataBindingUtil.bind(v);

        mBinding.connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirstFragment.this.onConnectClicked();
            }
        });
        mBinding.urlEditBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mBinding.encryptionIcon.setEnabled(s.length() == 0 || s.toString().startsWith("wss://"));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        WebSocket ws = mActivityProxy.getClientManager().getWebSocket();
        if (ws != null) {
            ws.closeAsync(CloseStatusCode.NORMAL_CLOSURE, "normal closure");
        }
        mViewWidth = container.getWidth();

        return v;
    }

    @Override
    public void onDestroyView() {
        mBinding.unbind();
        super.onDestroyView();
    }

    void onConnectClicked() {
        String text = mBinding.urlEditBox.getText().toString();
        if (text.length() == 0) {
            text = SampleApp.getTextRes(R.string.url_hint);
        }
        try {
            URI uri = new URI(text);
            mBinding.progressCircle.setVisibility(View.VISIBLE);
            mActivityProxy.getClientManager().open(uri, new ClientManager.ConnectionListener() {
                @Override
                public void onConnected() {
                    Log.i(TAG, "Connected");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isResumed()) {
                                return;
                            }
                            mBinding.progressCircle.setVisibility(View.GONE);
                        }
                    });

                    if (mActivityProxy != null) {
                        mActivityProxy.onConnected();
                    }
                }

                @Override
                public void onConnectionFailed(final Exception e) {
                    Log.w(TAG, "Connection Failed");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isResumed()) {
                                return;
                            }
                            mBinding.progressCircle.setVisibility(View.GONE);
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
