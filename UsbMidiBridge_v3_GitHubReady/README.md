# UsbMidiBridge v0.3.0

This version fixes the most likely compile error in the earlier source:
`container.$context()` is a `Context`, not guaranteed to be an `Activity`.

It also removes the empty `@UsesPermissions` annotation and includes a real icon.

## Build
This project is GitHub-ready. Put the files into a GitHub repository and run:
Actions -> Build AIX -> Run workflow.

The official MIT App Inventor build system packages external components into `.aix`.
See the MIT source/build documentation.

## App Inventor blocks after import
- AndroidApiLevel
- DeviceCount
- ListDevices
- OpenDevice(index, port)
- OpenFirstOutputDevice
- CloseDevice
- Status(text)
- Connected(name)
- Disconnected()
- MidiMessage(data)

Example:
ListDevices -> `0:My MIDI Keyboard:0:1`
OpenDevice(0, 0)

A Note On C4 commonly arrives as:
`144,60,velocity`
A Note Off commonly arrives as:
`128,60,0`
or Note On with velocity 0.
