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
package org.dkaukov.esp32.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.dkaukov.esp32.io.SerialTransport;

public class SlipLoggingSerialTransport implements SerialTransport {
  private final SerialTransport delegate;
  private final PrintWriter logWriter;
  private final long startTime = System.nanoTime();
  private boolean inFrame = false;

  private final ByteArrayOutputStream writeBuffer = new ByteArrayOutputStream();
  private final ByteArrayOutputStream readBuffer = new ByteArrayOutputStream();

  public SlipLoggingSerialTransport(SerialTransport delegate, Path logFilePath) throws IOException {
    this.delegate = delegate;
    this.logWriter = new PrintWriter(Files.newBufferedWriter(logFilePath));
  }

  private String hexDump(byte[] data) {
    return IntStream.range(0, data.length)
      .mapToObj(i -> String.format("%02X", data[i]))
      .collect(Collectors.joining(" "));
  }

  private String timestamp() {
    long now = System.nanoTime();
    double seconds = (now - startTime) / 1_000_000_000.0;
    return String.format("[%.3f]", seconds);
  }

  @Override
  public void write(byte[] buffer, int length) throws IOException {
    for (int i = 0; i < length; i++) {
      byte b = buffer[i];
      writeBuffer.write(b);
      if (b == (byte) 0xC0 && writeBuffer.size() > 1) { // end of SLIP frame
        byte[] frame = writeBuffer.toByteArray();
        logWriter.printf("%s >>>> (%6d): %s%n", timestamp(), frame.length, hexDump(frame));
        logWriter.flush();
        writeBuffer.reset();
      }
    }
    delegate.write(buffer, length);
  }

  @Override
  public int read(byte[] buffer, int length) throws IOException {
    int read = delegate.read(buffer, length);
    for (int i = 0; i < read; i++) {
      byte b = buffer[i];
      if (b == (byte) 0xC0) {
        if (inFrame) {
          readBuffer.write(b);
          logWriter.printf("%s <<<< (%6d): %s%n", timestamp(), readBuffer.size(), hexDump(readBuffer.toByteArray()));
          logWriter.flush();
          readBuffer.reset();
          inFrame = false;
        } else {
          readBuffer.reset();
          readBuffer.write(b);
          inFrame = true;
        }
      } else  {
        if (inFrame) {
          readBuffer.write(b);
        }
      }
    }
    return read;
  }

  @Override
  public void setControlLines(boolean dtr, boolean rts) throws IOException {
    logWriter.printf("%s SET_CONTROL_LINES DTR=%s RTS=%s%n", timestamp(), dtr, rts);
    logWriter.flush();
    delegate.setControlLines(dtr, rts);
  }

  public void close() {
    logWriter.close();
  }
}

