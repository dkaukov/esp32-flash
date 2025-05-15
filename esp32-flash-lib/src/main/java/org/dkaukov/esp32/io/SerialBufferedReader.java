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
package org.dkaukov.esp32.io;

import java.io.IOException;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class SerialBufferedReader {

  private final SerialTransport transport;
  private final byte[] buffer;
  private int bufferPos = 0;
  private int bufferLimit = 0;

  public static class SerialReadException extends RuntimeException {
    public SerialReadException(IOException cause) {
      super("Failed to read from serial transport", cause);
    }
  }

  /**
   * Creates a new SerialBufferedReader with the specified buffer size.
   *
   * @param transport the SerialTransport to read from
   * @param bufferSize the size of the buffer
   * @throws IllegalArgumentException if bufferSize is less than or equal to 0
   */
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public SerialBufferedReader(SerialTransport transport, int bufferSize) {
    this.transport = transport;
    this.buffer = new byte[bufferSize];
  }

  /**
   * Reads a byte from the serial transport.
   *
   * @return an Optional containing the read byte, or an empty Optional if no byte is available
   * @throws SerialReadException if an I/O error occurs while reading
   */
  public Optional<Byte> read() {
    if (bufferPos >= bufferLimit) {
      try {
        bufferLimit = transport.read(buffer, buffer.length);
        bufferPos = 0;
      } catch (IOException e) {
        throw new SerialReadException(e);
      }
    }
    return (bufferPos >= bufferLimit) ? Optional.empty() : Optional.of(buffer[bufferPos++]);
  }

  /**
   * Clears the buffer.
   */
  public void flush() {
    bufferPos = 0;
    bufferLimit = 0;
  }
}
