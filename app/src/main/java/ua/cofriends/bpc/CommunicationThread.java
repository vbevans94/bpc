package ua.cofriends.bpc;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

public class CommunicationThread extends Thread {

    private final static String TAG = CommunicationThread.class.getSimpleName();
    private static final UUID APP_UUID = UUID.fromString("e8e10f95-1a70-4b27-9ccf-02010264e9c8");

    private final BluetoothSocket mSocket;
    private final BlockingQueue<Command> mQueue;
    private final WeakReference<OnConnected> mListenerRef;
    private OutputStream mOutStream;

    public CommunicationThread(BluetoothDevice device, BlockingQueue<Command> queue, OnConnected listener) {
        // Use a temporary object that is later assigned to mSocket,
        // because mSocket is final
        BluetoothSocket tmp = null;

        // Get a BluetoothSocket to connect with the given BluetoothDevice
        try {
            // APP_UUID is the app's UUID string, also used by the server code
            tmp = device.createRfcommSocketToServiceRecord(APP_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Connection failed", e);
        }
        mSocket = tmp;
        mQueue = queue;
        mListenerRef = new WeakReference<OnConnected>(listener);
    }

    @Override
    public void run() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // Cancel discovery because it will slow down the connection
        bluetoothAdapter.cancelDiscovery();

        try {
            // Connect the device through the socket. This will block
            // until it succeeds or throws an exception
            mSocket.connect();
            mOutStream = mSocket.getOutputStream();
        } catch (IOException connectException) {
            // Unable to connect; close the socket and get out
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close failed", e);
            }
            return;
        }

        OnConnected listener = mListenerRef.get();
        if (listener != null) {
            listener.onConnected();
        }

        try {
            while (true) {
                Command command = mQueue.take();
                mOutStream.write(command.toString().getBytes());
                mOutStream.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cancels an in-progress connection, and closes the socket.
     */
    public void cancel() {
        try {
            mSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface OnConnected {

        void onConnected();
    }

    public static class Command {

        public static Command LEFT = new Command(Type.LEFT, null);
        public static Command RIGHT = new Command(Type.RIGHT, null);

        private final Type mType;
        private final String mData;

        private Command(Type type, String data) {
            mData = data;
            mType = type;
        }

        public static Command start(String data) {
            return new Command(Type.START, data);
        }

        @Override
        public String toString() {
            return mData == null
                ? String.format("%s%n", mType.toString())
                : String.format("%s|%s%n", mType.toString(), mData);
        }

        public enum Type {
            LEFT, RIGHT, START
        }
    }
}