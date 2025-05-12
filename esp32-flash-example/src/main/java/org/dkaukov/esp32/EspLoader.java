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
package org.dkaukov.esp32;

import com.fazecast.jSerialComm.SerialPort;

import org.dkaukov.esp32.chip.FlashRegion;
import org.dkaukov.esp32.core.EspFlasherApi;
import org.dkaukov.esp32.io.ProgressCallback;
import org.dkaukov.esp32.io.SerialTransport;


public class EspLoader {

  static SerialPort comPort;

  public static void main(String[] args) {
    // get the first port available, you might want to change that
    comPort = SerialPort.getCommPorts()[6];
    System.out.println("Connected to: \"" + comPort.getDescriptivePortName() + "\"");
    comPort.setBaudRate(EspFlasherApi.ESP_ROM_BAUD);
    comPort.openPort();
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
    System.out.println("done ");
    comPort.closePort();
  }

  private static ProgressCallback getCallBack() {
    return new ProgressCallback() {
      @Override
      public void onProgress(float pct) {
        System.out.printf("\rProgress: %.2f%%", pct);
      }
      @Override
      public void onEnd() {
        System.out.println();
      }
      @Override
      public void onInfo(String value) {
        System.out.println(value);
      }
    };
  }

  private static SerialTransport getComPort() {
    return new SerialTransport() {
      @Override
      public int read(byte[] buffer, int length) {
        return comPort.readBytes(buffer, length);
      }
      @Override
      public void write(byte[] buffer, int length) {
        comPort.writeBytes(buffer, length);
      }
      @Override
      public void setControlLines(boolean dtr, boolean rts) {
        if (dtr) {
          comPort.setDTR();
        }
        if (rts) {
          comPort.setRTS();
        }
        if (!dtr) {
          comPort.clearDTR();
        }
        if (!rts) {
          comPort.clearRTS();
        }
      }
    };
  }
}
