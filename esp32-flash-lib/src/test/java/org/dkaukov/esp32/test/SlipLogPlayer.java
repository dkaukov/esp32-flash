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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.dkaukov.esp32.io.SerialTransport;
import org.dkaukov.esp32.utils.Utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SlipLogPlayer implements SerialTransport {
  private enum Direction { READ, WRITE, CONTROL }

  private static class LogEntry {
    final double timestamp;
    final Direction direction;
    final byte[] data;
    final Boolean dtr, rts;

    LogEntry(double timestamp, Direction direction, byte[] data) {
      this.timestamp = timestamp;
      this.direction = direction;
      this.data = data;
      this.dtr = null;
      this.rts = null;
    }

    LogEntry(double timestamp, boolean dtr, boolean rts) {
      this.timestamp = timestamp;
      this.direction = Direction.CONTROL;
      this.dtr = dtr;
      this.rts = rts;
      this.data = null;
    }
  }

  private final List<LogEntry> logEntries;
  private int index = 0;
  private ByteArrayInputStream currentReadBuffer = null;
  private double nextTimestamp = 0;
  private int readDelay = 0;

  public SlipLogPlayer(Path logFile) throws IOException {
    this.logEntries = parseLogFile(logFile);
  }

  @Override
  public void write(byte[] buffer, int length) {
    LogEntry entry = nextEntry();
    if (entry.direction != Direction.WRITE)
      throw new AssertionError("Expected WRITE entry but got: " + entry.direction);
    if (!Arrays.equals(Arrays.copyOf(buffer, length), entry.data))
      throw new AssertionError("WRITE data mismatch at timestamp: " + entry.timestamp);
    if (currentReadBuffer != null) {
      currentReadBuffer = null;
    }
  }

  @Override
  public int read(byte[] buffer, int length) {
    if (currentReadBuffer == null || currentReadBuffer.available() == 0) {
      if (readDelay > 0) {
        Utils.delayMS(readDelay);
        readDelay = 0;
        return 0;
      }
      LogEntry entry = nextEntry();
      if (entry.direction != Direction.READ)
        throw new AssertionError("Expected READ entry but got: " + entry.direction);
      currentReadBuffer = new ByteArrayInputStream(entry.data);
      readDelay = (int) Math.round((nextTimestamp - entry.timestamp) * 1100.0);
    }
    return currentReadBuffer.read(buffer, 0, length);
  }

  @Override
  public void setControlLines(boolean dtr, boolean rts) {
    LogEntry entry = nextEntry();
    if (entry.direction != Direction.CONTROL) {
      throw new AssertionError("Expected CONTROL entry but got: " + entry.direction);
    }
    if (!Objects.equals(entry.dtr, dtr) || !Objects.equals(entry.rts, rts)) {
      throw new AssertionError(String.format("Control line mismatch at %.3f: expected DTR=%s RTS=%s but got DTR=%s RTS=%s",
          entry.timestamp, entry.dtr, entry.rts, dtr, rts));
    }
    Utils.delayMS((int) Math.round((nextTimestamp - entry.timestamp) * 1000.0));
  }

  private LogEntry nextEntry() {
    if (index >= logEntries.size())
      throw new NoSuchElementException("No more log entries");
    LogEntry res = logEntries.get(index++);
    if (index < logEntries.size()) {
      nextTimestamp = logEntries.get(index).timestamp;
    }
    return res;
  }

  private static List<LogEntry> parseLogFile(Path path) throws IOException {
    List<LogEntry> entries = new ArrayList<>();
    for (String line : Files.readAllLines(path)) {
      if (line.trim().isEmpty()) continue;
      double timestamp = Double.parseDouble(line.substring(1, line.indexOf(']')));
      line = line.substring(line.indexOf(']') + 1).trim();

      if (line.startsWith("SET_CONTROL_LINES")) {
        boolean dtr = line.contains("DTR=true");
        boolean rts = line.contains("RTS=true");
        entries.add(new LogEntry(timestamp, dtr, rts));
      } else if (line.startsWith(">>>>") || line.startsWith("<<<<")) {
        Direction dir = line.startsWith(">>>>") ? Direction.WRITE : Direction.READ;
        String[] parts = line.split(":", 2);
        String[] hexBytes = parts[1].trim().split(" ");
        byte[] data = new byte[hexBytes.length];
        for (int i = 0; i < hexBytes.length; i++)
          data[i] = (byte) Integer.parseInt(hexBytes[i], 16);
        entries.add(new LogEntry(timestamp, dir, data));
      } else {
        throw new IOException("Unrecognized log line: " + line);
      }
    }
    return entries;
  }

  public boolean isFinished() {
    return index >= logEntries.size();
  }
}
