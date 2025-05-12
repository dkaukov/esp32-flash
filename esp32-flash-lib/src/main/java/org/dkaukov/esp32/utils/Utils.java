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
package org.dkaukov.esp32.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;
import java.util.zip.Deflater;

import javax.annotation.Nonnull;

/**
 * Utility class providing various helper methods for ESP32-related operations.
 */
public class Utils {

  private static final byte SLIP_SEPARATOR = (byte) 0xC0;
  private static final int ESP_CHECKSUM_MAGIC = 0xEF;

  // Private constructor to prevent instantiation
  private Utils() {
  }

  /**
   * Delays the current thread for a specified number of milliseconds.
   *
   * @param timeout the delay duration in milliseconds
   */
  public static void delayMS(int timeout) {
    try {
      Thread.sleep(timeout);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  /**
   * Calculates the checksum of the given byte array using the ESP checksum algorithm.
   *
   * @param data the byte array to calculate the checksum for
   * @return the calculated checksum
   */
  public static int calcChecksum(byte[] data) {
    int chk = ESP_CHECKSUM_MAGIC;
    for (byte b : data) {
      chk ^= (b & 0xFF);
    }
    return chk;
  }

  /**
   * Converts a byte array to a hexadecimal string representation.
   *
   * @param bytes the byte array to convert
   * @return the hexadecimal string representation of the byte array
   */
  public static String printHex(byte[] bytes) {
    if (bytes == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < bytes.length; i++) {
      sb.append(String.format("0x%02x", bytes[i]));
      if (i < bytes.length - 1) {
        sb.append(",");
      }
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Converts a byte array to a compact hexadecimal string representation.
   *
   * @param bytes the byte array to convert
   * @return the compact hexadecimal string representation of the byte array
   */
  public static String printHex2(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (byte aByte : bytes) {
      sb.append(String.format("%02x", aByte));
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Encodes a byte array using the SLIP protocol.
   *
   * @param buffer the byte array to encode
   * @return the SLIP-encoded byte array
   */
  public static byte[] slipEncode(byte[] buffer) {
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    encoded.write(SLIP_SEPARATOR); // Start of SLIP frame
    for (byte b : buffer) {
      if (b == SLIP_SEPARATOR) {
        encoded.write(0xDB);
        encoded.write(0xDC);
      } else if (b == (byte) 0xDB) {
        encoded.write(0xDB);
        encoded.write(0xDD);
      } else {
        encoded.write(b);
      }
    }
    encoded.write(SLIP_SEPARATOR); // End of SLIP frame
    return encoded.toByteArray();
  }

  /**
   * Decodes a SLIP-encoded byte array.
   *
   * @param buffer the SLIP-encoded byte array to decode
   * @return the decoded byte array
   */
  public static byte[] slipDecode(byte[] buffer) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    boolean inEscape = false;
    for (byte b : buffer) {
      if (inEscape) {
        if (b == (byte) 0xDC) {
          out.write(0xC0);
        } else if (b == (byte) 0xDD) {
          out.write(0xDB);
        } else {
          // Invalid escape, write as-is or handle error
          out.write(b);
        }
        inEscape = false;
      } else if (b == (byte) 0xDB) {
        inEscape = true;
      } else {
        out.write(b);
      }
    }
    return out.toByteArray();
  }

  /**
   * Compresses a byte array using the Deflater compression algorithm.
   *
   * @param uncompressedData the byte array to compress
   * @return the compressed byte array
   */
  public static byte[] compressBytes(byte[] uncompressedData) {
    Deflater compressor = new Deflater();
    compressor.setLevel(Deflater.BEST_COMPRESSION);
    compressor.setInput(uncompressedData);
    compressor.finish();
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedData.length)) {
      byte[] buf = new byte[1024];
      while (!compressor.finished()) {
        int count = compressor.deflate(buf);
        bos.write(buf, 0, count);
      }
      return bos.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Computes the MD5 hash of the given byte array and returns it as a hexadecimal string.
   *
   * @param data the byte array to hash
   * @return the MD5 hash as a hexadecimal string
   */
  public static String md5(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return printHex2(md.digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("MD5 algorithm not available", e);
    }
  }

  /**
   * Measures the execution time of a task and passes the duration to a callback.
   *
   * @param task     the task to measure
   * @param callback the callback to receive the execution time in milliseconds
   */
  public static void time(Runnable task, @Nonnull Consumer<Long> callback) {
    long start = System.nanoTime();
    task.run();
    long durationMs = (System.nanoTime() - start) / 1_000_000;
    callback.accept(durationMs);
  }
}
