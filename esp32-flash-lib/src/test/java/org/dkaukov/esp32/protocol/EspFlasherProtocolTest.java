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
import org.junit.jupiter.api.Test;

class EspFlasherProtocolTest {

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
  static void setUp() throws IOException {
    // get the first port available, you might want to change that
    comPort = SerialPort.getCommPorts()[6];
    System.out.println("Connected to: \"" + comPort.getDescriptivePortName() + "\"");
    comPort.setBaudRate(EspFlasherApi.ESP_ROM_BAUD);
    comPort.openPort();
  }

  @Test
  void sync() throws IOException {
    protocol = new EspFlasherProtocol(new SlipLoggingSerialTransport(new TestTransport(comPort), Path.of("src/test/resources/sync.txt")));
    protocol.enterBootLoader();
    protocol.sync();
    protocol.reset();
  }

  @Test
  void detectChip() throws IOException {
    protocol = new EspFlasherProtocol(new SlipLoggingSerialTransport(new TestTransport(comPort), Path.of("src/test/resources/detect-chip.txt")));
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.reset();
  }

  @Test
  void loadStub() throws IOException {
    protocol = new EspFlasherProtocol(new SlipLoggingSerialTransport(new TestTransport(comPort), Path.of("src/test/resources/load-stub.txt")));
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.reset();
  }

  @Test
  void writeFlash() throws IOException {
    byte [] data = new byte[1024];
    protocol = new EspFlasherProtocol(new SlipLoggingSerialTransport(new TestTransport(comPort), Path.of("src/test/resources/write-flash.txt")));
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashWrite(data, 0x400, 0x0000);
    protocol.reset();
  }

  @Test
  void eraseFlash() throws IOException {
    byte [] data = new byte[1024];
    protocol = new EspFlasherProtocol(new SlipLoggingSerialTransport(new TestTransport(comPort), Path.of("src/test/resources/erase-flash.txt")));
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.eraseFlash();
    protocol.reset();
  }
}