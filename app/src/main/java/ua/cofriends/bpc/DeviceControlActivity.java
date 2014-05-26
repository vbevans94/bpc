package ua.cofriends.bpc;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class DeviceControlActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks
        , GoogleApiClient.OnConnectionFailedListener, CommunicationThread.OnConnected {

    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    private static final int RESOLVE_CONNECTION_REQUEST_CODE = 3;
    private static final int REQUEST_CODE_OPENER = 2;
    public static final String KEY_METADATA_LINK = "key_metadata";

    @InjectView(R.id.text_connection_state)
    TextView mTextConnectionState;
    @InjectView(R.id.web_slides)
    WebView mWebSlides;

    private boolean mConnected;
    private BluetoothDevice mDevice;
    private CommunicationThread mCommunicationThread;
    private GoogleApiClient mGoogleApiClient;
    private final BlockingQueue<CommunicationThread.Command> mQueue = new ArrayBlockingQueue<CommunicationThread.Command>(16);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.activity_device);

        ButterKnife.inject(this);

        final Intent intent = getIntent();
        mDevice = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        ((TextView) findViewById(R.id.device_address)).setText(mDevice.getAddress());

        mWebSlides.setWebViewClient(new SlideClient());
        WebSettings webSettings = mWebSlides.getSettings();
        webSettings.setJavaScriptEnabled(true);

        getSupportActionBar().setTitle(mDevice.getName());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addScope(Drive.SCOPE_APPFOLDER) // required for App Folder sample
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case RESOLVE_CONNECTION_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;

            case REQUEST_CODE_OPENER:
                if (resultCode == RESULT_OK) {
                    DriveId driveId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    getIntent().putExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID, driveId);
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_control, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_disconnect:
                mCommunicationThread.cancel();
                mCommunicationThread = null;
                break;

            case R.id.menu_connect:
                if (getIntent().hasExtra(KEY_METADATA_LINK)) {
                    String link = getIntent().getStringExtra(KEY_METADATA_LINK);
                    mCommunicationThread = new CommunicationThread(mDevice, mQueue, this);
                    mCommunicationThread.start();
                    mQueue.offer(CommunicationThread.Command.start(link));
                }
                break;

            case android.R.id.home:
                onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @OnClick(R.id.button_left)
    @SuppressWarnings("unused")
    public void onLeftClicked(View v) {
        mWebSlides.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
        mWebSlides.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT));
        mQueue.offer(CommunicationThread.Command.LEFT);
    }

    @OnClick(R.id.button_right)
    @SuppressWarnings("unused")
    public void onRightClicked(View v) {
        mWebSlides.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        mWebSlides.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));
        mQueue.offer(CommunicationThread.Command.RIGHT);
    }

    private void showMessage(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (getIntent().hasExtra(KEY_METADATA_LINK)) {
            return;
        }
        if (getIntent().hasExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID)) {
            DriveId driveId = (DriveId) getIntent().getParcelableExtra(
                    OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
            getIntent().putExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID, driveId);

            DriveFile file = Drive.DriveApi.getFile(mGoogleApiClient, driveId);
            file.getMetadata(mGoogleApiClient)
                    .setResultCallback(mMetadataCallback);

            setSupportProgressBarIndeterminateVisibility(true);
        } else {
            IntentSender intentSender = Drive.DriveApi
                    .newOpenFileActivityBuilder()
                    .setMimeType(new String[]{"application/vnd.google-apps.presentation"})
                    .build(mGoogleApiClient);
            try {
                startIntentSenderForResult(intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                Log.w(TAG, "Unable to send intent", e);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
        }
    }

    final private ResultCallback<DriveResource.MetadataResult> mMetadataCallback = new
            ResultCallback<DriveResource.MetadataResult>() {
                @Override
                public void onResult(DriveResource.MetadataResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Problem while trying to fetch metadata");
                        return;
                    }
                    Metadata metadata = result.getMetadata();
                    String embedLink = metadata.getEmbedLink();
                    getIntent().putExtra(KEY_METADATA_LINK, embedLink);

                    mWebSlides.loadUrl(embedLink);

                    setSupportProgressBarIndeterminateVisibility(false);
                }
            };

    @Override
    public void onConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnected = true;
                mTextConnectionState.setText(R.string.connected);
                supportInvalidateOptionsMenu();
            }
        });
    }

    private static class SlideClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            Context context = view.getContext();
            if (context instanceof DeviceControlActivity) {
                DeviceControlActivity activity = (DeviceControlActivity) context;
                String slidesUrl = activity.getIntent().getStringExtra(KEY_METADATA_LINK);
                if (url.startsWith(slidesUrl)) {
                    view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S));
                    view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S));
                }
            }
        }
    }
}
