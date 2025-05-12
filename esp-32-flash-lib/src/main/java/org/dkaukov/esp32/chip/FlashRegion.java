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
public enum FlashRegion {
  BOOTLOADER(0x1000, 0x8000),
  APP_BOOTLOADER(0xe000, 0x2000), // sometimes same as PARTITION_TABLE
  PARTITION_TABLE(0x8000, 0x1000),
  APP_0(0x10000, 0x1F0000), // typical default size
  APP_1(0x210000, 0x1F0000),
  NVS(0x9000, 0x6000);

  private final int defaultOffset;
  private final int defaultSize;

  FlashRegion(int offset, int size) {
    this.defaultOffset = offset;
    this.defaultSize = size;
  }

}
