/*
 * This file is licensed under the GNU General Public License v3.0.
 *
 * You may obtain a copy of the License at
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package org.dkaukov.esp32.core;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.dkaukov.esp32.chip.Esp32ChipId;
import org.dkaukov.esp32.io.ProgressCallback;
import org.dkaukov.esp32.io.SerialTransport;
import org.dkaukov.esp32.protocol.EspFlasherProtocol;
import org.dkaukov.esp32.utils.Utils;

public final class EspFlasherApi {

  public static final int ESP_ROM_BAUD = 115200;
  public static final int ESP_ROM_BAUD_HIGH = 460800;
  public static final int ESP_ROM_BAUD_HIGHEST = 921600;
  private static final String CHIP_PLACEHOLDER = "{chip}";

  private final EspFlasherProtocol protocol;
  private boolean useDeflate = true;

  private EspFlasherApi(EspFlasherProtocol protocol) {
    this.protocol = protocol;
  }

  public static StartStage connect(SerialTransport transport) {
    EspFlasherProtocol proto = new EspFlasherProtocol(transport);
    proto.enterBootLoader();
    proto.sync();
    return new EspFlasherApi(proto).new StartStage();
  }

  public final class StartStage {

    public StartStage withBaudRate(int baudRate, IntConsumer baudRateSupplier) {
      protocol.changeBaudRate(baudRate);
      baudRateSupplier.accept(baudRate);
      return this;
    }

    public StartStage withCallBack(ProgressCallback callBack) {
      protocol.setProgressCallback(callBack);
      return this;
    }

    public DetectedStage chipDetect() {
      protocol.detectChip();
      return new DetectedStage();
    }
  }

  public final class DetectedStage {

    public StubReadyStage loadStub() {
      protocol.loadStub();
      return new StubReadyStage();
    }

    public Esp32ChipId getChipId() {
      return protocol.getChipId();
    }

    public RomReadyStage spiAttach() {
      protocol.espSpiAttach();
      return new RomReadyStage();
    }
  }

  public final class StubReadyStage {

    public StubReadyStage eraseFlash() {
      protocol.eraseFlash();
      return this;
    }

    public StubReadyStage eraseFlashRegion(int offset, int length) {
      protocol.eraseFlashRegion(offset, length);
      return this;
    }

    public StubReadyStage writeFlash(int offset, byte[] data) {
      return writeFlash(offset, data, true);
    }

    public StubReadyStage writeFlash(int offset, byte[] data, boolean verify) {
      if (useDeflate) {
        protocol.flashDeflWrite(data, 0x4000, offset);
      } else {
        protocol.flashWrite(data, 0x4000, offset);
      }
      return verify ? verifyFlash(offset, data) : this;
    }

    public StubReadyStage readFlash(byte[] dst, int offset, int length) {
      protocol.readFlash(dst, offset, length);
      return this;
    }

    public StubReadyStage verifyFlash(int offset, byte[] data) {
      protocol.flashMd5Verify(data, offset);
      return this;
    }

    public Esp32ChipId getChipId() {
      return protocol.getChipId();
    }

    public StubReadyStage withStub(Consumer<StubReadyStage> stubConsumer) {
      stubConsumer.accept(this);
      return this;
    }

    public StubReadyStage withCompression(boolean compression) {
      useDeflate = compression;
      return this;
    }

    public byte[] readFile(String pathStr) {
      return FileUtils.readFile(pathStr.replace(CHIP_PLACEHOLDER, protocol.getChipId().getReadableName()));
    }

    public byte[] readResource(String pathStr) {
      return FileUtils.readResource(pathStr.replace(CHIP_PLACEHOLDER, protocol.getChipId().getReadableName()));
    }

    public void reset() {
      protocol.reset();
    }

    public StubReadyStage softReset() {
      if (getChipId() != Esp32ChipId.ESP8266) {
        throw new IllegalStateException("Soft resetting is currently only supported on ESP8266");
      }
      protocol.runUserCode();
      return this;
    }
  }

  public final class RomReadyStage {

    public RomReadyStage writeFlash(int offset, byte[] data) {
      return writeFlash(offset, data, true);
    }

    public RomReadyStage writeFlash(int offset, byte[] data, boolean verify) {
      if (useDeflate) {
        protocol.flashDeflWrite(data, 0x400, offset);
      } else {
        protocol.flashWrite(data, 0x400, offset);
      }
      return verify ? verifyFlash(offset, data) : this;
    }

    public RomReadyStage verifyFlash(int offset, byte[] data) {
      protocol.flashMd5Verify(data, offset);
      return this;
    }

    public Esp32ChipId getChipId() {
      return protocol.getChipId();
    }

    public RomReadyStage withRom(Consumer<RomReadyStage> romConsumer) {
      romConsumer.accept(this);
      return this;
    }

    public RomReadyStage withCompression(boolean compression) {
      useDeflate = compression;
      return this;
    }

    public byte[] readFile(String pathStr) {
      return FileUtils.readFile(pathStr.replace(CHIP_PLACEHOLDER, protocol.getChipId().getReadableName()));
    }

    public byte[] readResource(String pathStr) {
      return FileUtils.readResource(pathStr.replace(CHIP_PLACEHOLDER, protocol.getChipId().getReadableName()));
    }

    public void reset() {
      protocol.reset();
    }
  }

  public static void delayMs(int timeout) {
    Utils.delayMS(timeout);
  }

  public static class FileUtils {

    private FileUtils() {
    }

    public static byte[] readFile(Path path) {
      try {
        return Files.readAllBytes(path);
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read file: " + path, e);
      }
    }

    public static byte[] readFile(String pathStr) {
      return readFile(Path.of(pathStr));
    }

    public static byte[] readResource(String resourcePath) {
      try (InputStream is = EspFlasherApi.class.getClassLoader().getResourceAsStream(resourcePath)) {
        if (is == null) {
          throw new FileNotFoundException("Resource not found: " + resourcePath);
        }
        return is.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read resource: " + resourcePath, e);
      }
    }

    public static byte[] readStream(InputStream inputStream) {
      try (InputStream is = inputStream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read input stream", e);
      }
    }
  }
}
