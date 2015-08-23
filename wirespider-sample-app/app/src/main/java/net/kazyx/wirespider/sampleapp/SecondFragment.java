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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import net.kazyx.wirespider.WebSocketHandler;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class SecondFragment extends Fragment {
    public static SecondFragment newInstance() {
        return new SecondFragment();
    }

    private ActivityProxy mActivityProxy;

    private ClientManager mManager;

    @Bind(R.id.message_edit_box)
    EditText mMessageBox;

    @Bind(R.id.log_console)
    ListView mLogConsole;

    private List<String> mLogMessages = new ArrayList<>();
    private ArrayAdapter mAdapter;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        mLogMessages.add(getString(R.string.ready));
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
        View v = inflater.inflate(R.layout.fragment_second, container, false);
        ButterKnife.bind(this, v);

        mAdapter = new ArrayAdapter<>(inflater.getContext(), R.layout.console_row, mLogMessages);
        mLogConsole.setAdapter(mAdapter);
        mManager.setWebSocketHandler(mHandler);
        mViewWidth = container.getWidth();
        return v;
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    @OnClick(R.id.sendButton)
    void onClickSend(Button b) {
        String text = mMessageBox.getText().toString();
        if (text.length() == 0) {
            text = getString(R.string.message_hint);
        }
        mManager.getWebSocket().sendTextMessageAsync(text);
        updateConsole(getString(R.string.client_to_server) + text);
    }

    private WebSocketHandler mHandler = new WebSocketHandler() {
        @Override
        public void onTextMessage(String message) {
            if (!isVisible()) {
                return;
            }
            updateConsole(getString(R.string.server_to_client) + message);
        }

        @Override
        public void onBinaryMessage(byte[] message) {
            if (!isVisible()) {
                return;
            }
            updateConsole(getString(R.string.server_to_client) + "binary message " + message.length + " bytes");
        }

        @Override
        public void onClosed(int code, String reason) {
            if (!isVisible()) {
                return;
            }
            updateConsole(getString(R.string.connection_closed));

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mActivityProxy != null) {
                        mActivityProxy.onDisconnected();
                    }
                }
            }, 1000);
        }
    };

    private void updateConsole(final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mLogMessages.add(text);
                mAdapter.notifyDataSetChanged();
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