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
package org.dkaukov.esp32.chip;

import lombok.Getter;

@Getter
public enum RomErrorCode {
  INVALID_MESSAGE((byte) 0x05, "Received message is invalid (parameters or length field is invalid)"),
  ACTION_FAILED((byte) 0x06, "Failed to act on received message"),
  INVALID_CRC((byte) 0x07, "Invalid CRC in message"),
  FLASH_WRITE_ERROR((byte) 0x08, "Flash write error - verification mismatch after writing to flash"),
  FLASH_READ_ERROR((byte) 0x09, "Flash read error - SPI read failed"),
  FLASH_READ_LENGTH_ERROR((byte) 0x0A, "Flash read length error - SPI read request length is too long"),
  DEFLATE_ERROR((byte) 0x0B, "Deflate error - compressed uploads only"),
  UNKNOWN((byte) 0x00, "Unknown ROM error");

  private final byte code;
  private final String message;

  RomErrorCode(byte code, String message) {
    this.code = code;
    this.message = message;
  }

  public static RomErrorCode from(byte code) {
    for (RomErrorCode e : values()) {
      if (e.code == code) {
        return e;
      }
    }
    return UNKNOWN;
  }
}
