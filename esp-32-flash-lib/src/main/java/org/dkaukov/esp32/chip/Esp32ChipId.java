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

import java.util.Map;
import java.util.Set;

import lombok.Getter;

public enum Esp32ChipId {
  ESP8266(0x8266, null, "ESP8266", Set.of(0xfff0c101), Map.of()),
  ESP32(0x32, "stubs/1/esp32.json", "ESP32", Set.of(0x00f01d83), Map.of(
    FlashRegion.BOOTLOADER, 0x1000,
    FlashRegion.APP_BOOTLOADER, 0xe000,
    FlashRegion.PARTITION_TABLE, 0x8000,
    FlashRegion.APP_0, 0x10000,
    FlashRegion.APP_1, 0x210000,
    FlashRegion.NVS, 0x9000
  )),
  ESP32S2(0x3252, "stubs/1/esp32s2.json", "ESP32-S2", Set.of(0x000007c6), Map.of(
    FlashRegion.BOOTLOADER, 0x0000,
    FlashRegion.APP_BOOTLOADER, 0xe000,
    FlashRegion.PARTITION_TABLE, 0x8000,
    FlashRegion.APP_0, 0x10000,
    FlashRegion.APP_1, 0x210000,
    FlashRegion.NVS, 0x9000
  )),
  ESP32S3(0x3253, "stubs/1/esp32s3.json", "ESP32-S3", Set.of(0x9), Map.of(
    FlashRegion.BOOTLOADER, 0x0000,
    FlashRegion.APP_BOOTLOADER, 0xe000,
    FlashRegion.PARTITION_TABLE, 0x8000,
    FlashRegion.APP_0, 0x10000,
    FlashRegion.APP_1, 0x210000,
    FlashRegion.NVS, 0x9000
  )),
  ESP32H2(0x3282, "stubs/1/esp32h2.json", "ESP32-H2", Set.of(0xca26cc22, 0xd7b73e80), Map.of(
    FlashRegion.BOOTLOADER, 0x0000,
    FlashRegion.APP_BOOTLOADER, 0xe000,
    FlashRegion.PARTITION_TABLE, 0x8000,
    FlashRegion.APP_0, 0x10000,
    FlashRegion.APP_1, 0x210000,
    FlashRegion.NVS, 0x9000
  )),
  ESP32C2(0x32C2, null, "ESP32-C2", Set.of(0x6f51306f, 2084675695), Map.of(
    FlashRegion.BOOTLOADER, 0x0000,
    FlashRegion.APP_BOOTLOADER, 0xe000,
    FlashRegion.PARTITION_TABLE, 0x8000,
    FlashRegion.APP_0, 0x10000,
    FlashRegion.APP_1, 0x210000,
    FlashRegion.NVS, 0x9000
  )),
  ESP32C3(0x32C3, "stubs/1/esp32c3.json", "ESP32-C3", Set.of(0x6921506f, 0x1b31506f), Map.of(
    FlashRegion.BOOTLOADER, 0x0000,
    FlashRegion.APP_BOOTLOADER, 0xe000,
    FlashRegion.PARTITION_TABLE, 0x8000,
    FlashRegion.APP_0, 0x10000,
    FlashRegion.APP_1, 0x210000,
    FlashRegion.NVS, 0x9000
  )),
  ESP32C6(0x32C6, "stubs/1/esp32c6.json", "ESP32-C6", Set.of(0x0da1806f, 752910447), Map.of(
    FlashRegion.BOOTLOADER, 0x0000,
    FlashRegion.APP_BOOTLOADER, 0xe000,
    FlashRegion.PARTITION_TABLE, 0x8000,
    FlashRegion.APP_0, 0x10000,
    FlashRegion.APP_1, 0x210000,
    FlashRegion.NVS, 0x9000
  ));

  @Getter
  private final int id;
  @Getter
  private final String stubName;
  @Getter
  private final String readableName;

  private final Set<Integer> magicValues;
  private final Map<FlashRegion, Integer> flashRegions;

  Esp32ChipId(int id, String stubName, String readableName, Set<Integer> magicValues,
    Map<FlashRegion, Integer> flashRegions) {
    this.id = id;
    this.stubName = stubName;
    this.readableName = readableName;
    this.magicValues = magicValues;
    this.flashRegions = flashRegions;
  }

  public int getRegion(FlashRegion region) {
    return flashRegions.getOrDefault(region, region.getDefaultOffset());
  }

  public static Esp32ChipId fromId(int id) {
    for (Esp32ChipId chip : values()) {
      if (chip.id == id) {
        return chip;
      }
    }
    throw new IllegalArgumentException("Unknown ESP chip ID: 0x" + Integer.toHexString(id));
  }

  public static Esp32ChipId fromMagicValue(int magic) {
    for (Esp32ChipId chip : values()) {
      if (chip.magicValues.contains(magic)) {
        return chip;
      }
    }
    throw new IllegalArgumentException("Unknown ESP magic value: 0x" + Integer.toHexString(magic));
  }

  @Override
  public String toString() {
    return readableName + " (0x" + Integer.toHexString(id).toUpperCase() + ")";
  }
}
