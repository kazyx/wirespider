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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import net.kazyx.wirespider.WebSocketHandler;
import net.kazyx.wirespider.extension.Extension;
import net.kazyx.wirespider.sampleapp.databinding.SecondFragmentBinding;

public class SecondFragment extends Fragment {
    public static SecondFragment newInstance() {
        return new SecondFragment();
    }

    private SecondFragmentBinding mBinding;

    private ActivityProxy mActivityProxy;

    private ClientManager mManager;

    private final MessageAdapter mAdapter = new MessageAdapter();

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
    }

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
        mManager = mActivityProxy.getClientManager();
    }

    @Override
    public void onDetach() {
        mActivityProxy = null;
        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View v = inflater.inflate(R.layout.second_fragment, container, false);
        mBinding = DataBindingUtil.bind(v);

        mBinding.sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SecondFragment.this.onSendClicked();
            }
        });
        mBinding.logConsole.setAdapter(mAdapter);

        mManager.setWebSocketHandler(mHandler);
        mViewWidth = container.getWidth();

        updateConsole(getString(R.string.ready), MessageAdapter.Type.OTHER);
        for (Extension ext : mManager.getWebSocket().extensions()) {
            updateConsole("Extension: " + ext.name(), MessageAdapter.Type.OTHER);
        }

        return v;
    }

    @Override
    public void onDestroyView() {
        mBinding.unbind();
        super.onDestroyView();
    }

    void onSendClicked() {
        String text = mBinding.messageEditBox.getText().toString();
        if (text.length() == 0) {
            text = getString(R.string.hello);
        }
        mManager.getWebSocket().sendTextMessageAsync(text);
        mAdapter.add(getString(R.string.client_to_server) + text, MessageAdapter.Type.SENT);
        mAdapter.notifyDataSetChanged();
    }

    private WebSocketHandler mHandler = new WebSocketHandler() {
        @Override
        public void onTextMessage(String message) {
            updateConsole(getString(R.string.server_to_client) + message, MessageAdapter.Type.RECEIVED);
        }

        @Override
        public void onBinaryMessage(byte[] message) {
            updateConsole(getString(R.string.server_to_client) + "binary message " + message.length + " bytes", MessageAdapter.Type.RECEIVED);
        }

        @Override
        public void onClosed(int code, String reason) {
            updateConsole(getString(R.string.connection_closed), MessageAdapter.Type.OTHER);

            if (mActivityProxy != null) {
                mActivityProxy.onDisconnected();
            }
        }
    };

    private void updateConsole(final String text, final MessageAdapter.Type type) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (isResumed()) {
                    mAdapter.add(text, type);
                    mAdapter.notifyDataSetChanged();
                    if (mBinding.logConsole.getLastVisiblePosition() < mAdapter.getCount() - 1) {
                        mBinding.logConsole.smoothScrollToPosition(mAdapter.getCount());
                    }
                }
            }
        });
    }

    private int mViewWidth;

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN && enter) {
            Animator anim = ObjectAnimator.ofFloat(getView(), "x", mViewWidth, 0.0f);
            anim.setDuration(700).setInterpolator(new OvershootInterpolator());
            return anim;
        } else {
            return super.onCreateAnimator(transit, enter, nextAnim);
        }
    }
}
