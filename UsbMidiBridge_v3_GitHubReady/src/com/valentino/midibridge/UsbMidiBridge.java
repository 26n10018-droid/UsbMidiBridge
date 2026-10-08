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
    version = 3,
    versionName = "0.3.0",
    description = "USB/Android MIDI bridge for MIT App Inventor. Reads MIDI keyboards connected through USB OTG.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "images/icon.png"
)
@SimpleObject(external = true)
public class UsbMidiBridge extends AndroidNonvisibleComponent implements Component {

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MidiManager midiManager;
    private MidiDevice device;
    private MidiOutputPort outputPort;

    public UsbMidiBridge(ComponentContainer container) {
        super(container.$form());
        context = container.$context();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            midiManager = (MidiManager) context.getSystemService(Context.MIDI_SERVICE);
        }
    }

    @SimpleEvent(description = "Status text.")
    public void Status(String text) {
        EventDispatcher.dispatchEvent(this, "Status", text);
    }

    @SimpleEvent(description = "A MIDI device was opened.")
    public void Connected(String name) {
        EventDispatcher.dispatchEvent(this, "Connected", name);
    }

    @SimpleEvent(description = "The current MIDI device was closed.")
    public void Disconnected() {
        EventDispatcher.dispatchEvent(this, "Disconnected");
    }

    @SimpleEvent(description = "Raw MIDI bytes as comma-separated unsigned decimal values.")
    public void MidiMessage(String data) {
        EventDispatcher.dispatchEvent(this, "MidiMessage", data);
    }

    @SimpleFunction(description = "Returns Android API level.")
    public int AndroidApiLevel() {
        return Build.VERSION.SDK_INT;
    }

    @SimpleFunction(description = "Returns the number of MIDI devices visible to Android.")
    public int DeviceCount() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || midiManager == null) {
            return 0;
        }
        return midiManager.getDevices().length;
    }

    @SimpleFunction(description = "Returns devices as index:name:inputPorts:outputPorts separated by semicolons.")
    public String ListDevices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || midiManager == null) {
            return "";
        }

        MidiDeviceInfo[] infos = midiManager.getDevices();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < infos.length; i++) {
            if (i > 0) result.append(";");

            MidiDeviceInfo info = infos[i];
            String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);

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

    @SimpleFunction(description = "Opens a MIDI device index and OUTPUT port. A USB MIDI keyboard normally sends data through an OUTPUT port.")
    public void OpenDevice(final int index, final int port) {
        CloseDevice();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || midiManager == null) {
            Status("Android MIDI unavailable. Requires Android 6.0/API 23+.");
            return;
        }

        final MidiDeviceInfo[] infos = midiManager.getDevices();

        if (index < 0 || index >= infos.length) {
            Status("Invalid device index: " + index);
            return;
        }

        final MidiDeviceInfo info = infos[index];

        if (port < 0 || port >= info.getOutputPortCount()) {
            Status("Invalid OUTPUT port: " + port);
            return;
        }

        midiManager.openDevice(info, new MidiManager.OnDeviceOpenedListener() {
            @Override
            public void onDeviceOpened(MidiDevice openedDevice) {
                if (openedDevice == null) {
                    Status("Android failed to open MIDI device.");
                    return;
                }

                device = openedDevice;

                try {
                    outputPort = device.openOutputPort(port);

                    if (outputPort == null) {
                        Status("Could not open OUTPUT port " + port);
                        CloseDevice();
                        return;
                    }

                    outputPort.connect(new MidiReceiver() {
                        @Override
                        public void onSend(byte[] data, int offset, int count, long timestamp) {
                            StringBuilder bytes = new StringBuilder();

                            for (int i = offset; i < offset + count; i++) {
                                if (bytes.length() > 0) {
                                    bytes.append(",");
                                }
                                bytes.append(data[i] & 0xFF);
                            }

                            final String message = bytes.toString();

                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    MidiMessage(message);
                                }
                            });
                        }
                    });

                    String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);

                    if (name == null || name.length() == 0) {
                        name = "MIDI Device";
                    }

                    final String connectedName = name;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Connected(connectedName);
                            Status("Connected: " + connectedName + " / OUT " + port);
                        }
                    });

                } catch (Exception e) {
                    Status("MIDI error: " + e.getMessage());
                    CloseDevice();
                }
            }
        }, mainHandler);
    }

    @SimpleFunction(description = "Opens the first Android MIDI device with an OUTPUT port.")
    public void OpenFirstOutputDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || midiManager == null) {
            Status("Android MIDI unavailable.");
            return;
        }

        MidiDeviceInfo[] infos = midiManager.getDevices();

        for (int i = 0; i < infos.length; i++) {
            if (infos[i].getOutputPortCount() > 0) {
                OpenDevice(i, 0);
                return;
            }
        }

        Status("No MIDI device with an OUTPUT port found.");
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
        Disconnected();
    }
}
