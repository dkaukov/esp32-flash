# ESP32 Flash

**ESP32 Flash** is a Java library for flashing firmware to ESP32 devices using the serial protocol implemented in the ESP32 ROM bootloader and esptool stub loader. It provides a fluent, modular, and extensible API for embedding ESP flashing logic into your own tools or UIs.

## ✨ Features

* 🧱 **Modular Core Library** — Just bring your own `SerialTransport`.
* 🔁 **ROM & Stub Protocol Support** — Handles both ESP32 ROM bootloader and stub loader protocols.
* 📦 **Fluent API** — Easily script multi-step flashing processes in one expressive chain.
* 🧪 **Optional Flash Verification** — Built-in MD5 verification for written regions.
* 📊 **Callback Hooks** — Track progress and status updates during operations.

## 🔧 You Provide the Transport

This is a library — it does **not** bundle a serial port implementation.
You must provide a `SerialTransport` implementation suitable for your environment (e.g., JSSC, RXTX, jSerialComm, or your own).

```java
public interface SerialTransport {
    int read(byte[] buffer, int length);
    void write(byte[] buffer, int length);
    void setControlLines(boolean dtr, boolean rts);
}
```

This keeps the core clean, platform-agnostic, and easy to integrate into desktop or embedded Java applications.

## 💡 Example: Fluent API Usage

```java
EspFlasherApi.connect(getComPort())
    .withCallBack(getCallBack())
    .withBaudRate(EspFlasherApi.ESP_ROM_BAUD_HIGH, comPort::setBaudRate)
    .chipDetect()
    .loadStub()
    .eraseFlash()
    .withStub(stub -> stub
        .withCompression(false)
        .writeFlash(stub.getChipId().getRegion(FlashRegion.BOOTLOADER), stub.readResource("{chip}/Blink.ino.bootloader.bin"), true)
        .writeFlash(stub.getChipId().getRegion(FlashRegion.PARTITION_TABLE), stub.readResource("{chip}/Blink.ino.partitions.bin"), true)
        .writeFlash(stub.getChipId().getRegion(FlashRegion.APP_BOOTLOADER), stub.readResource("{chip}/boot_app0.bin"), true)
        .withCompression(true)
        .writeFlash(stub.getChipId().getRegion(FlashRegion.APP_0), stub.readResource("{chip}/Blink.ino.bin"), true))
    .reset();
```

## 📦 Project Structure

* `esp32-flash-lib` — Core flashing library, reusable in any Java app.
* `esp32-flash-example` — Basic runnable example showing how to integrate the library with a real serial port and flashing flow.

## ▶️ Getting Started

### Requirements

* Java 11+
* Maven

### Build the Library

```bash
git clone https://github.com/dkaukov/esp32-flash.git
cd esp32-flash
mvn clean install
```

### Run the Example

```bash
cd esp32-flash-example
mvn exec:java -Dexec.mainClass="org.dkaukov.esp32.EspLoader"
```

Replace `"your.Main"` with the correct entry point in your code.

## 📁 Resources

Place your binaries (`bootloader.bin`, `partitions.bin`, `app.bin`, etc.) in your resource path or use your own binary loader.

## 📜 License

This project is licensed under the terms of the [GPL-3.0 License](LICENSE).

