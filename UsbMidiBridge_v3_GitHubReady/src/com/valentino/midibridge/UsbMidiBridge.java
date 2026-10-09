package com.valentino.midibridge;

import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

@DesignerComponent(
    version = 4,
    versionName = "0.4.0",
    description = "USB OTG MIDI keyboard bridge with parsed MIDI messages.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "images/icon.png"
)
@SimpleObject(external = true)
public class UsbMidiBridge
        extends AndroidNonvisibleComponent
        implements Component {

    private final Context context;
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private MidiManager midiManager;
    private MidiDevice device;
    private MidiOutputPort outputPort;

    // MIDI stream parser
    private int runningStatus = -1;
    private int expectedDataBytes = 0;
    private int dataCount = 0;
    private final int[] messageData = new int[2];

    public UsbMidiBridge(ComponentContainer container) {
        super(container.$form());
        context = container.$context();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            midiManager = (MidiManager)
                    context.getSystemService(Context.MIDI_SERVICE);
        }
    }

    @SimpleEvent(description = "Status text.")
    public void Status(String text) {
        EventDispatcher.dispatchEvent(this, "Status", text);
    }

    @SimpleEvent(description = "MIDI device connected.")
    public void Connected(String name) {
        EventDispatcher.dispatchEvent(this, "Connected", name);
    }

    @SimpleEvent(description = "MIDI device disconnected.")
    public void Disconnected() {
        EventDispatcher.dispatchEvent(this, "Disconnected");
    }

    @SimpleEvent(
        description = "Parsed MIDI message: status,note,velocity or controller,value."
    )
    public void MidiMessage(String data) {
        EventDispatcher.dispatchEvent(this, "MidiMessage", data);
    }

    @SimpleFunction(description = "Returns Android API level.")
    public int AndroidApiLevel() {
        return Build.VERSION.SDK_INT;
    }

    @SimpleFunction(description = "Returns number of MIDI devices.")
    public int DeviceCount() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || midiManager == null) {
            return 0;
        }

        return midiManager.getDevices().length;
    }

    @SimpleFunction(
        description = "Lists devices as index:name:inputPorts:outputPorts."
    )
    public String ListDevices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || midiManager == null) {
            return "";
        }

        MidiDeviceInfo[] infos = midiManager.getDevices();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < infos.length; i++) {
            if (i > 0) {
                result.append(";");
            }

            MidiDeviceInfo info = infos[i];
            String name = info.getProperties().getString(
                    MidiDeviceInfo.PROPERTY_NAME);

            if (name == null || name.length() == 0) {
                name = "MIDI Device";
            }

            name = name.replace(";", ",").replace(":", "-");

            result.append(i)
                  .append(":")
                  .append(name)
                  .append(":")
                  .append(info.getInputPortCount())
                  .append(":")
                  .append(info.getOutputPortCount());
        }

        return result.toString();
    }

    @SimpleFunction(
        description = "Opens a MIDI device index and OUTPUT port."
    )
    public void OpenDevice(final int index, final int port) {
        CloseDevice();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || midiManager == null) {
            postStatus("Android MIDI requires Android 6.0/API 23+.");
            return;
        }

        final MidiDeviceInfo[] infos = midiManager.getDevices();

        if (index < 0 || index >= infos.length) {
            postStatus("Invalid device index: " + index);
            return;
        }

        final MidiDeviceInfo info = infos[index];

        if (port < 0 || port >= info.getOutputPortCount()) {
            postStatus("Invalid OUTPUT port: " + port);
            return;
        }

        midiManager.openDevice(
            info,
            new MidiManager.OnDeviceOpenedListener() {
                @Override
                public void onDeviceOpened(MidiDevice openedDevice) {
                    if (openedDevice == null) {
                        postStatus("Failed to open MIDI device.");
                        return;
                    }

                    device = openedDevice;

                    try {
                        outputPort = device.openOutputPort(port);

                        if (outputPort == null) {
                            postStatus("Could not open OUTPUT port.");
                            CloseDevice();
                            return;
                        }

                        // Reset parser whenever a device is opened.
                        resetParser();

                        outputPort.connect(new MidiReceiver() {
                            @Override
                            public void onSend(
                                    byte[] data,
                                    int offset,
                                    int count,
                                    long timestamp) {

                                synchronized (UsbMidiBridge.this) {
                                    parseMidiBytes(data, offset, count);
                                }
                            }
                        });

                        String name = info.getProperties().getString(
                                MidiDeviceInfo.PROPERTY_NAME);

                        if (name == null || name.length() == 0) {
                            name = "MIDI Device";
                        }

                        final String connectedName = name;

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Connected(connectedName);
                                Status("Connected: " + connectedName
                                        + " / OUT " + port);
                            }
                        });

                    } catch (Exception e) {
                        postStatus("MIDI error: " + e.getMessage());
                        CloseDevice();
                    }
                }
            },
            mainHandler
        );
    }

    @SimpleFunction(
        description = "Opens the first MIDI device with an OUTPUT port."
    )
    public void OpenFirstOutputDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || midiManager == null) {
            postStatus("Android MIDI unavailable.");
            return;
        }

        MidiDeviceInfo[] infos = midiManager.getDevices();

        for (int i = 0; i < infos.length; i++) {
            if (infos[i].getOutputPortCount() > 0) {
                OpenDevice(i, 0);
                return;
            }
        }

        postStatus("No MIDI device with an OUTPUT port found.");
    }

    /*
     * Parse incoming MIDI bytes. Realtime bytes (F8-FF) are ignored.
     * Supports running status and channel messages.
     */
    private void parseMidiBytes(byte[] data, int offset, int count) {
        for (int i = offset; i < offset + count; i++) {
            int value = data[i] & 0xFF;

            // MIDI realtime messages may appear between any MIDI bytes.
            if (value >= 0xF8) {
                continue;
            }

            if ((value & 0x80) != 0) {
                // Status byte
                dataCount = 0;

                if (value >= 0xF0) {
                    // System common messages are not used for piano notes.
                    runningStatus = -1;
                    expectedDataBytes = 0;
                    continue;
                }

                runningStatus = value;
                int command = value & 0xF0;

                // Program Change and Channel Pressure use one data byte.
                if (command == 0xC0 || command == 0xD0) {
                    expectedDataBytes = 1;
                } else {
                    expectedDataBytes = 2;
                }

                continue;
            }

            // Ignore data until a valid channel status is received.
            if (runningStatus < 0 || expectedDataBytes == 0) {
                continue;
            }

            if (dataCount < messageData.length) {
                messageData[dataCount++] = value;
            }

            if (dataCount >= expectedDataBytes) {
                int status = runningStatus;
                int command = status & 0xF0;
                int first = messageData[0];
                int second = expectedDataBytes > 1
                        ? messageData[1] : 0;

                // Note Off, Note On, and Control Change only.
                if (command == 0x80
                        || command == 0x90
                        || command == 0xB0) {

                    final String message =
                            status + "," + first + "," + second;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MidiMessage(message);
                        }
                    });
                }

                // Keep runningStatus to support MIDI running status.
                dataCount = 0;
            }
        }
    }

    private synchronized void resetParser() {
        runningStatus = -1;
        expectedDataBytes = 0;
        dataCount = 0;
    }

    private void postStatus(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Status(message);
            }
        });
    }

    @SimpleFunction(description = "Closes the current MIDI device.")
    public void CloseDevice() {
        try {
            if (outputPort != null) {
                outputPort.close();
            }
        } catch (Exception ignored) {
        }

        outputPort = null;

        try {
            if (device != null) {
                device.close();
            }
        } catch (Exception ignored) {
        }

        device = null;
        resetParser();
        Disconnected();
    }
}
