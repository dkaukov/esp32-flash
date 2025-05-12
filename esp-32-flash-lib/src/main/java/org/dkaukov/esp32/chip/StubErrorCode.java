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
public enum StubErrorCode {
  INVALID_SIZE((byte) 0x01, "Invalid size"),
  INVALID_ARGUMENT((byte) 0x02, "Invalid argument"),
  FLASH_READ_ERROR((byte) 0x03, "Flash read error"),
  FLASH_WRITE_ERROR((byte) 0x04, "Flash write error"),
  FLASH_ERASE_ERROR((byte) 0x05, "Flash erase error"),
  FLASH_ARGS_ERROR((byte) 0x06, "Invalid flash arguments"),
  FLASH_TIMEOUT((byte) 0x07, "Flash timeout"),
  UNKNOWN((byte) 0x00, "Unknown stub error");

  private final byte code;
  private final String message;

  StubErrorCode(byte code, String message) {
    this.code = code;
    this.message = message;
  }

  public static StubErrorCode from(byte code) {
    for (StubErrorCode e : values()) {
      if (e.code == code) {
        return e;
      }
    }
    return UNKNOWN;
  }
}
