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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.dkaukov.esp32.test.SlipLogPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EspFlasherProtocolTest {

  private EspFlasherProtocol protocol;
  private SlipLogPlayer player;

  private EspFlasherProtocol getProtocol(String trace) throws IOException {
    player = new SlipLogPlayer(Path.of("src/test/resources/" + trace));
    return new EspFlasherProtocol(player);
  }

  @Test
  @DisplayName("Test the sync operation of the protocol.")
  void sync() throws IOException {
    protocol = getProtocol("sync.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test the chip detection functionality of the protocol.")
  void detectChip() throws IOException {
    protocol = getProtocol("detect-chip.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test loading a stub into the ESP32.")
  void loadStub() throws IOException {
    protocol = getProtocol("load-stub.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test writing data to the flash memory of the ESP32.")
  void writeFlash() throws IOException {
    byte[] data = new byte[1024];
    protocol = getProtocol("write-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashWrite(data, 0x400, 0x0000);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test erasing the flash memory of the ESP32.")
  void eraseFlash() throws IOException {
    protocol = getProtocol("erase-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.eraseFlash();
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test writing data to the memory of the ESP32.")
  void writeMem() throws IOException {
    byte[] data = new byte[1024];
    protocol = getProtocol("write-mem.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.memWrite(data, 0x1800, 0x0000);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test verifying the flash memory of the ESP32 using an MD5 checksum.")
  void verifyFlash() throws IOException {
    byte[] data = new byte[1024];
    protocol = getProtocol("verify-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashMd5Verify(data, 0x0000);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test writing compressed data to the flash memory of the ESP32.")
  void writeDeflFlash() throws IOException {
    byte[] data = new byte[1024];
    protocol = getProtocol("write-defl-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.flashDeflWrite(data, 0x400, 0x0000);
    protocol.reset();
    assertTrue(player.isFinished());
  }
}