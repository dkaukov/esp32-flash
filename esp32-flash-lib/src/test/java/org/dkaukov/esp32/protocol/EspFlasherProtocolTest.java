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

import org.dkaukov.esp32.test.SlipLogPlayer;
import org.junit.jupiter.api.Test;

class EspFlasherProtocolTest {

  private EspFlasherProtocol protocol;

  private static EspFlasherProtocol getProtocol(String trace) throws IOException {
    return new EspFlasherProtocol(new SlipLogPlayer(Path.of(trace)));
  }

  @Test
  void sync() throws IOException {
    protocol = getProtocol("src/test/resources/sync.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.reset();
  }

  @Test
  void detectChip() throws IOException {
    protocol = getProtocol("src/test/resources/detect-chip.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.reset();
  }

  @Test
  void loadStub() throws IOException {
    protocol = getProtocol("src/test/resources/load-stub.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.reset();
  }

  @Test
  void writeFlash() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("src/test/resources/write-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashWrite(data, 0x400, 0x0000);
    protocol.reset();
  }

  @Test
  void eraseFlash() throws IOException {
    protocol = getProtocol("src/test/resources/erase-flash.txt");
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
    protocol = getProtocol("src/test/resources/write-mem.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.memWrite(data, 0x1800, 0x0000);
    protocol.reset();
  }

  @Test
  void verifyFlash() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("src/test/resources/verify-flash.txt");
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
    protocol = getProtocol("src/test/resources/write-defl-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashDeflWrite(data, 0x400, 0x0000);
    protocol.reset();
  }
}