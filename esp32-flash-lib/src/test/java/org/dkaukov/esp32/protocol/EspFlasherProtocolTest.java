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
import java.lang.reflect.Method;
import java.nio.file.Path;

import org.dkaukov.esp32.io.ProgressCallback;
import org.dkaukov.esp32.test.SlipLogPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class EspFlasherProtocolTest {

  private EspFlasherProtocol protocol;
  private SlipLogPlayer player;

  private EspFlasherProtocol getProtocol(String trace) throws IOException {
    player = new SlipLogPlayer(Path.of("src/test/resources/" + trace));
    EspFlasherProtocol res = new EspFlasherProtocol(player);
    res.setProgressCallback(new ProgressCallback() {
      @Override
      public void onInfo(String value) {
        log.info(value);
      }
    });
    return res;
  }

  @BeforeEach
  void logTestInfo(TestInfo testInfo) {
    log.info("Running test: {} [{}()]", testInfo.getDisplayName(), testInfo.getTestMethod().map(Method::getName).orElse("unknown"));
  }

  @AfterEach
  void logTestEnd() {
    log.info("--------------------------------------------------------------------");
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

  @Test
  @DisplayName("Test erasing a specific region of the flash memory of the ESP32.")
  void eraseFlashRegion() throws IOException {
    protocol = getProtocol("erase-flash-region.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.eraseFlashRegion(0x0000, 0x400);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test reading a specific region of the flash memory of the ESP32.")
  void readFlashRegion() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("read-flash-region.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.readFlash(data, 0x0000, 0x400);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test running user code on the ESP32.")
  void runUserCode() throws IOException {
    protocol = getProtocol("run-user-code.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.runUserCode();
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test updating a specific register on the ESP32.")
  void updateReg() throws IOException {
    protocol = getProtocol("update-reg.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.updateReg(0x0000, 0xFFFFF, 0x1234);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test ending the flash operation on the ESP32.")
  void endFlash() throws IOException {
    protocol = getProtocol("end-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.endFlash(true);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test ending the deflate flash operation on the ESP32.")
  void endDeflFlash() throws IOException {
    protocol = getProtocol("end-defl-flash.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.loadStub();
    protocol.endDeflFlash(true);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test writing data to the flash memory of the ESP32 without using a stub.")
  void writeFlashNoStub() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("write-flash-no-stub.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.espSpiAttach();
    protocol.flashWrite(data, 0x400, 0x0000);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test writing compressed data to the flash memory of the ESP32 without using a stub.")
  void writeDeflFlashNoStub() throws IOException {
    byte [] data = new byte[1024];
    protocol = getProtocol("write-defl-flash-no-stub.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.detectChip();
    protocol.espSpiAttach();
    protocol.flashDeflWrite(data, 0x400, 0x0000);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test changing the baud rate of the ESP32.")
  void changeBaudRate() throws IOException {
    protocol = getProtocol("change-baud-rate.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.changeBaudRate(115200);
    protocol.reset();
    assertTrue(player.isFinished());
  }

  @Test
  @DisplayName("Test setting the flash size of the ESP32.")
  void setFlashSize() throws IOException {
    protocol = getProtocol("set-flash-size.txt");
    protocol.enterBootLoader();
    protocol.sync();
    protocol.setFlashSize(1024 * 1024 * 4);
    protocol.reset();
    assertTrue(player.isFinished());
  }
}