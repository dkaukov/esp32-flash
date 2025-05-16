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
package org.dkaukov.esp32.protocol;

import java.io.IOException;
import java.nio.file.Path;

import com.fazecast.jSerialComm.SerialPort;

import org.dkaukov.esp32.core.EspFlasherApi;
import org.dkaukov.esp32.io.SerialTransport;
import org.dkaukov.esp32.test.SlipLoggingSerialTransport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
class TraceGeneratorTest {

  static SerialPort comPort;
  private EspFlasherProtocol protocol;

  static class TestTransport implements SerialTransport {
    private final SerialPort port;
    TestTransport(SerialPort port) {
      this.port = port;
    }

    @Override
    public int read(byte[] buffer, int length) {
      return port.readBytes(buffer, length);
    }

    @Override
    public void write(byte[] buffer, int length) {
      port.writeBytes(buffer, length);
    }

    @Override
    public void setControlLines(boolean dtr, boolean rts) {
      if (dtr) {
        port.setDTR();
      }
      if (rts) {
        port.setRTS();
      }
      if (!dtr) {
        port.clearDTR();
      }
      if (!rts) {
        port.clearRTS();
      }
    }
  }

  @BeforeAll
  static void setUp() {
    // get the first port available, you might want to change that
    comPort = SerialPort.getCommPorts()[6];
    System.out.println("Connected to: \"" + comPort.getDescriptivePortName() + "\"");
    comPort.setBaudRate(EspFlasherApi.ESP_ROM_BAUD);
    comPort.openPort();
  }

  private static EspFlasherProtocol getProtocol(String file) throws IOException {
    return new EspFlasherProtocol(new SlipLoggingSerialTransport(new TestTransport(comPort), Path.of("src/test/resources/" + file)));
  }

  @Test
  void sync() throws IOException {
    protocol = getProtocol("sync.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.reset();
  }

  @Test
  void detectChip() throws IOException {
    protocol = getProtocol("detect-chip.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.reset();
  }

  @Test
  void loadStub() throws IOException {
    protocol = getProtocol("load-stub.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.reset();
  }

  @Test
  void writeFlash() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("write-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashWrite(data, 0x400, 0x0000);
    protocol.reset();
  }

  @Test
  void eraseFlash() throws IOException {
    protocol = getProtocol("erase-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.eraseFlash();
    protocol.reset();
  }

  @Test
  void writeMem() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("write-mem.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.memWrite(data, 0x1800, 0x0000);
    protocol.reset();
  }

  @Test
  void verifyFlash() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("verify-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashMd5Verify(data, 0x0000);
    protocol.reset();
  }

  @Test
  void writeDeflFlash() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("write-defl-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashDeflWrite(data, 0x400, 0x0000);
    protocol.reset();
  }

  @Test
  void eraseFlashRegion() throws IOException {
    protocol = getProtocol("erase-flash-region.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.eraseFlashRegion(0x0000, 0x400);
    protocol.reset();
  }

  @Test
  void readFlashRegion() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("read-flash-region.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.readFlash(data, 0x0000, 0x400);
    protocol.reset();
  }

  @Test
  void runUserCode() throws IOException {
    protocol = getProtocol("run-user-code.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.runUserCode();
    protocol.reset();
  }

  @Test
  void updateReg() throws IOException {
    protocol = getProtocol("update-reg.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.updateReg(0x0000, 0xFFFFF, 0x1234);
    protocol.reset();
  }

  @Test
  void endFlash() throws IOException {
    protocol = getProtocol("end-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.endFlash(true);
    protocol.reset();
  }

  @Test
  void endDeflFlash() throws IOException {
    protocol = getProtocol("end-defl-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.endDeflFlash(true);
    protocol.reset();
  }

  @Test
  void writeFlashNoStub() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("write-flash-no-stub.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.espSpiAttach();
    protocol.flashWrite(data, 0x400, 0x0000);
    protocol.reset();
  }

  @Test
  void writeDeflFlashNoStub() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("write-defl-flash-no-stub.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.espSpiAttach();
    protocol.flashDeflWrite(data, 0x400, 0x0000);
    protocol.reset();
  }

  @Test
  void changeBaudRate() throws IOException {
    protocol = getProtocol("change-baud-rate.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.changeBaudRate(115200);
    protocol.reset();
  }

  @Test
  void setFlashSize() throws IOException {
    protocol = getProtocol("set-flash-size.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.setFlashSize(1024 * 1024 * 4);
    protocol.reset();
  }
}