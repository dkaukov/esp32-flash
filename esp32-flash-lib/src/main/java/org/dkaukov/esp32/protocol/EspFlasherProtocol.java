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

import static org.dkaukov.esp32.core.EspFlasherApi.FileUtils.readResource;
import static org.dkaukov.esp32.utils.Utils._checksum;
import static org.dkaukov.esp32.utils.Utils.compressBytes;
import static org.dkaukov.esp32.utils.Utils.delayMS;
import static org.dkaukov.esp32.utils.Utils.md5;
import static org.dkaukov.esp32.utils.Utils.printHex;
import static org.dkaukov.esp32.utils.Utils.printHex2;
import static org.dkaukov.esp32.utils.Utils.slipDecode;
import static org.dkaukov.esp32.utils.Utils.slipEncode;
import static org.dkaukov.esp32.utils.Utils.time;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import org.dkaukov.esp32.io.ProgressCallback;
import org.dkaukov.esp32.io.SerialTransport;
import org.dkaukov.esp32.chip.StubErrorCode;
import org.dkaukov.esp32.chip.Esp32ChipId;
import org.dkaukov.esp32.chip.RomErrorCode;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Protocol class for ESP32/ESP8266 communication. This class handles the encoding and decoding of commands and
 * responses for the ESP32/ESP8266 bootloader protocol.
 * <a href="https://docs.espressif.com/projects/esptool/en/latest/esp32/advanced-topics/serial-protocol.html">...</a>
 */
@Slf4j
public class EspFlasherProtocol {

  // SLIP protocol
  private static final byte SLIP_SEPARATOR = (byte) 0xC0;
  private static final int MAX_RESPONSE_FRAME_SIZE = 1024 * 16;

  // Flashing timeouts (in ms)
  private static final class Timeout {

    private static final int DEFAULT = 3000;
    private static final int ERASE_REGION_PER_MB = 30_000;
    private static final int WRITE_REGION_PER_MB = 30_000;
    private static final int READ_REGION_PER_MB = 30_000;
    private static final int MD5_PER_MB = 8000;
    private static final int SYNC = 100;
    private static final int COMMAND_SHORT = 100;

    private Timeout() {
    }
  }

  // ROM bootloader commands
  private static final class RomCommand {

    private static final byte FLASH_BEGIN = 0x02;
    private static final byte FLASH_DATA = 0x03;
    private static final byte FLASH_END = 0x04;
    private static final byte MEM_BEGIN = 0x05;
    private static final byte MEM_END = 0x06;
    private static final byte MEM_DATA = 0x07;
    private static final byte SYNC = 0x08;
    private static final byte WRITE_REG = 0x09;
    private static final byte READ_REG = 0x0A;
    private static final byte SPI_SET_PARAMS = 0x0B;
    private static final byte SPI_ATTACH = 0x0D;
    private static final byte CHANGE_BAUDRATE = 0x0F;
    private static final byte FLASH_DEFL_BEGIN = 0x10;
    private static final byte FLASH_DEFL_DATA = 0x11;
    private static final byte FLASH_DEFL_END = 0x12;
    private static final byte SPI_FLASH_MD5 = 0x13;

    private RomCommand() {
    }
  }

  // Stub loader only commands
  private static final class StubCommand {

    private static final byte ERASE_FLASH = (byte) 0xD0;
    private static final byte ERASE_REGION = (byte) 0xD1;
    private static final byte READ_FLASH = (byte) 0xD2;
    private static final byte RUN_USER_CODE = (byte) 0xD3;

    private StubCommand() {
    }
  }

  // Misc constants
  private static final int MEM_WRITE_SIZE = 0x1800;
  private static final int CHIP_DETECT_MAGIC_REG_ADDRESS = 0x40001000;

  // Flash encryption support
  private static final Set<Esp32ChipId> CHIPS_WITH_FLASH_ENCRYPTION = EnumSet.of(
    Esp32ChipId.ESP32S3,
    Esp32ChipId.ESP32C2,
    Esp32ChipId.ESP32C3,
    Esp32ChipId.ESP32C6,
    Esp32ChipId.ESP32S2,
    Esp32ChipId.ESP32H2
  );

  @Getter
  private Esp32ChipId chipId;
  private final SerialTransport serialTransport;
  @Getter
  private boolean isStub = false;
  private @Nonnull ProgressCallback progressCallback = new ProgressCallback() {
  };

  interface EspCommand {
    CommandPacket toPacket();
  }

  interface EspReply {
    int getValue();
    byte[] getData();
    boolean isSuccess(boolean isStub);
    String errorMessage(boolean isStub);
  }

  @Data
  @Builder
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  static class CommandPacket {

    private final byte opcode;
    private int checksum;
    private final byte[] data;

    public byte[] getPacket() {
      ByteBuffer buf = ByteBuffer.allocate(8 + data.length);
      buf.order(ByteOrder.LITTLE_ENDIAN);      // ESP32 protocol uses little-endian
      buf.put((byte) 0x00);                    // Direction flag
      buf.put(opcode);                         // Operation
      buf.putShort((short) data.length);       // Length (2 bytes)
      buf.putInt(checksum);                    // Checksum (4 bytes)
      buf.put(data);                           // Payload
      return buf.array();
    }
  }

  @Data
  @Builder
  static class SyncCommand implements EspCommand {

    @Override
    public CommandPacket toPacket() {
      return CommandPacket.builder()
        .opcode(RomCommand.SYNC)
        .data(new byte[]{
          (byte) 0x07, (byte) 0x07, (byte) 0x12, (byte) 0x20,
          (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
          (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
          (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
          (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
          (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
          (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
          (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55})
        .build();
    }
  }

  @Data
  @Builder
  static class SpiAttachCommand implements EspCommand {

    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(0);
      payload.putInt(0);
      return CommandPacket.builder()
        .opcode(RomCommand.SPI_ATTACH)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
  static class SpiSetParamsCommand implements EspCommand {

    private final int id = 0;
    private final int totalSize;
    private final int blockSize = 64 * 1024;
    private final int sectorSize = 4 * 1024;
    private final int pageSize = 256;
    private final int statusMask = 0xffff;

    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(getId());
      payload.putInt(getTotalSize());
      payload.putInt(getBlockSize());
      payload.putInt(getSectorSize());
      payload.putInt(getPageSize());
      payload.putInt(getStatusMask());
      return CommandPacket.builder()
        .opcode(RomCommand.SPI_SET_PARAMS)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  static class FlashBeginCommand implements EspCommand {

    int size;
    int offset;
    int blockSize;
    int blocks;
    boolean canEncrypt;

    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16 + (canEncrypt ? 4 : 0)).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(size);
      payload.putInt(blocks);
      payload.putInt(blockSize);
      payload.putInt(offset);
      if (canEncrypt) {
        payload.putInt(0); // 0x00
      }
      return CommandPacket.builder()
        .opcode(RomCommand.FLASH_BEGIN)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  static class FlashDataCommand implements EspCommand {

    int sequence;
    byte[] chunk;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16 + chunk.length).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(chunk.length);   // Length of data chunk
      payload.putInt(sequence);       // Sequence number
      payload.putInt(0);        // 0x00
      payload.putInt(0);        // 0x00
      payload.put(chunk);             // Actual data
      return CommandPacket.builder()
        .opcode(RomCommand.FLASH_DATA)
        .data(payload.array())
        .checksum(_checksum(getChunk()))
        .build();
    }
  }

  @Data
  @Builder
  static class MemBeginCommand implements EspCommand {

    int size;
    int offset;
    int blockSize;
    int blocks;

    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(size);
      payload.putInt(blocks);
      payload.putInt(blockSize);
      payload.putInt(offset);
      return CommandPacket.builder()
        .opcode(RomCommand.MEM_BEGIN)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  static class MemDataCommand implements EspCommand {

    int sequence;
    byte[] chunk;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16 + chunk.length).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(chunk.length);   // Length of data chunk
      payload.putInt(sequence);       // Sequence number
      payload.putInt(0);        // 0x00
      payload.putInt(0);        // 0x00
      payload.put(chunk);             // Actual data
      return CommandPacket.builder()
        .opcode(RomCommand.MEM_DATA)
        .data(payload.array())
        .checksum(_checksum(getChunk()))
        .build();
    }
  }

  @Data
  @Builder
  static class MemEndCommand implements EspCommand {

    int entryPoint;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(0);        // 0x00
      payload.putInt(entryPoint);     // Entry point
      return CommandPacket.builder()
        .opcode(RomCommand.MEM_END)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  static class ChangeBaudRateCommand implements EspCommand {

    int baudRate;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(baudRate);       // Baud rate
      payload.putInt(0);        // 0x00
      return CommandPacket.builder()
        .opcode(RomCommand.CHANGE_BAUDRATE)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  static class FlashDeflBeginCommand implements EspCommand {

    int uncompressedSize;
    int offset;
    int blockSize;
    int blocks;
    boolean canEncrypt;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16 + (canEncrypt ? 4 : 0)).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(uncompressedSize);
      payload.putInt(blocks);
      payload.putInt(blockSize);
      payload.putInt(offset);
      if (canEncrypt) {
        payload.putInt(0); // 0x00
      }
      return CommandPacket.builder()
        .opcode(RomCommand.FLASH_DEFL_BEGIN)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  @SuppressFBWarnings("EI_EXPOSE_REP2")
  static class FlashDeflDataCommand implements EspCommand {

    int sequence;
    byte[] compressedChunk;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16 + compressedChunk.length)
        .order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(compressedChunk.length);
      payload.putInt(sequence);
      payload.putInt(0);  // Reserved
      payload.putInt(0);  // Reserved
      payload.put(compressedChunk);
      return CommandPacket.builder()
        .opcode(RomCommand.FLASH_DEFL_DATA)
        .data(payload.array())
        .checksum(_checksum(compressedChunk))
        .build();
    }
  }

  @Data
  @Builder
  static class SpiFlashMd5Command implements EspCommand {

    private final int address;
    private final int size;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(address);
      payload.putInt(size);
      payload.putInt(0);  // Reserved
      payload.putInt(0);  // Reserved
      return CommandPacket.builder()
        .opcode(RomCommand.SPI_FLASH_MD5)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  static class ReadRegCommand implements EspCommand {

    private final int address;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(address);
      return CommandPacket.builder()
        .opcode(RomCommand.READ_REG)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  static class FlashEndCommand implements EspCommand {

    private final int flag;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(flag);
      return CommandPacket.builder()
        .opcode(RomCommand.FLASH_END)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  static class FlashEraseCommand implements EspCommand {

    @Override
    public CommandPacket toPacket() {
      return CommandPacket.builder()
        .opcode(StubCommand.ERASE_FLASH)
        .data(new byte[]{})
        .build();
    }
  }

  @Data
  @Builder
  static class FlashEraseRegionCommand implements EspCommand {

    int offset;
    int size;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(offset);
      payload.putInt(size);
      return CommandPacket.builder()
        .opcode(StubCommand.ERASE_REGION)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  static class FlashReadCommand implements EspCommand {

    int offset;
    int size;
    int blockSize;
    int inFlightBlocks;

    @Override
    public CommandPacket toPacket() {
      ByteBuffer payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
      payload.putInt(offset);
      payload.putInt(size);
      payload.putInt(blockSize);
      payload.putInt(inFlightBlocks);
      return CommandPacket.builder()
        .opcode(StubCommand.READ_FLASH)
        .data(payload.array())
        .build();
    }
  }

  @Data
  @Builder
  @SuppressFBWarnings({"EI_EXPOSE_REP2", "EI_EXPOSE_REP"})
  public static class ResponsePacket implements EspReply {

    private final byte opcode;
    private final int value;
    private final byte[] data;

    public static Optional<ResponsePacket> from(final byte[] param, Integer len) {
      return Optional.ofNullable(param)
        .filter(p -> len >= 8)
        .map(ByteBuffer::wrap)
        .map(b -> b.order(ByteOrder.LITTLE_ENDIAN))
        .map(b -> {
          b.get();
          final byte opcode = b.get();
          final short size = b.getShort();
          final int value = b.getInt();
          byte[] data = new byte[size];
          b.get(data);
          return ResponsePacket.builder()
            .opcode(opcode)
            .value(value)
            .data(data)
            .build();
        });
    }

    public boolean isSuccess(boolean isStub) {
      if (isStub) {
        return data[data.length - 1] == 0x00;
      } else {
        return data[data.length - 4] == 0x00;
      }
    }

    public byte errorCode(boolean isStub) {
      if (isStub) {
        return data[data.length - 1];
      } else {
        return data[data.length - 3];
      }
    }

    public String errorMessage(boolean isStub) {
      if (isStub) {
        return StubErrorCode.from(errorCode(true)).getMessage();
      } else {
        return RomErrorCode.from(errorCode(false)).getMessage();
      }
    }
  }

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public EspFlasherProtocol(SerialTransport serialTransport) {
    this.serialTransport = serialTransport;
  }

  private EspReply exchange(EspCommand command, int timeoutMs, boolean verify) {
    CommandPacket commandPacket = command.toPacket();
    byte[] pkt = slipEncode(commandPacket.getPacket());
    log.trace("****: op: {}, len: {}, payload: {}", String.format("0x%02x", commandPacket.getOpcode()),
      commandPacket.getData().length, printHex2(commandPacket.getData()));
    log.trace(">>>>: {}: {}", pkt.length, printHex(pkt));
    serialTransport.write(pkt, pkt.length);
    EspReply res = waitForResponse(commandPacket.getOpcode(), timeoutMs);
    if (verify && !res.isSuccess(isStub)) {
      throw new ProtocolFatalException(command.getClass().getSimpleName() + " failed: " + res.errorMessage(isStub));
    }
    return res;
  }

  private EspReply exchange(EspCommand command, int timeoutMs) {
    return exchange(command, timeoutMs, true);
  }

  private byte[] extractFrame(ByteBuffer buffer) {
    int length = buffer.position();
    buffer.flip();
    byte[] frame = new byte[length];
    buffer.get(frame);
    return slipDecode(frame);
  }

  private Optional<Byte> readByte() {
    byte[] readBuf = new byte[1];
    return serialTransport.read(readBuf, 1) > 0 ? Optional.of(readBuf[0]) : Optional.empty();
  }

  private EspReply waitForResponse(byte opCode, int timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    ByteBuffer buffer = ByteBuffer.allocate(MAX_RESPONSE_FRAME_SIZE);
    AtomicBoolean inFrame = new AtomicBoolean(false);
    while (System.currentTimeMillis() < deadline) {
      Optional<ResponsePacket> res = readByte().flatMap(
          b -> {
            if (b == SLIP_SEPARATOR) {
              if (inFrame.get()) {
                // End of SLIP frame
                byte[] pkt = extractFrame(buffer);
                log.trace("<<<<: {}: {}", pkt.length, printHex(pkt));
                inFrame.set(false);
                return ResponsePacket.from(pkt, pkt.length);
              } else {
                buffer.clear();
                inFrame.set(true);
              }
            } else if (inFrame.get()) {
              if (buffer.hasRemaining()) {
                buffer.put(b);
              }
            }
            return Optional.empty();
          })
        .filter(p -> p.getOpcode() == opCode);
      if (res.isPresent()) {
        return res.get();
      }
    }
    throw new CommandTimeoutException(String.format("Timeout waiting for opcode 0x%02X", opCode));
  }

  private byte[] waitForResponse(byte[] pattern, int timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    ByteBuffer buffer = ByteBuffer.allocate(MAX_RESPONSE_FRAME_SIZE);
    boolean inFrame = false;
    while (System.currentTimeMillis() < deadline) {
      Optional<Byte> maybeByte = readByte();
      if (maybeByte.isEmpty()) {
        continue;
      }
      byte b = maybeByte.get();
      if (b == SLIP_SEPARATOR) {
        if (inFrame) {
          // End of SLIP frame
          byte[] pkt = extractFrame(buffer);
          log.trace("<<<<: {}: {}", pkt.length, printHex(pkt));
          inFrame = false;
          if (pattern == null || Arrays.equals(pattern, pkt)) {
            return pkt;
          }
        } else {
          buffer.clear();
          inFrame = true;
        }
      } else if (inFrame) {
        if (buffer.hasRemaining()) {
          buffer.put(b);
        }
      }
    }
    throw new CommandTimeoutException(String.format("Timeout waiting for pattern %s", printHex(pattern)));
  }

  private int timeoutPerMb(int seconds_per_mb, int size_bytes) {
    int result = (int) (seconds_per_mb * ((double) size_bytes / (double) 1000000));
    return Math.max(result, Timeout.DEFAULT);
  }

  public void flashWrite(byte[] image, int blockSize, int flashOffset) {
    int blocks = (image.length + blockSize - 1) / blockSize;
    progressCallback.onStart();
    progressCallback.onInfo(String.format("Writing %d bytes at 0x%08X...", image.length, flashOffset));
    time(() -> exchange(FlashBeginCommand.builder()
        .size(image.length)
        .blocks(blocks)
        .blockSize(blockSize)
        .offset(flashOffset)
        .canEncrypt(CHIPS_WITH_FLASH_ENCRYPTION.contains(chipId) && !isStub)
        .build(), timeoutPerMb(Timeout.ERASE_REGION_PER_MB, image.length))
      , t -> {
        if (!isStub) {
          progressCallback.onInfo(String.format(
            "Took %.2f seconds to erase %d bytes at 0x%08x", t / 1000.0, image.length, flashOffset)
          );
        }
      });
    time(() -> {
      for (int seq = 0; seq < blocks; seq++) {
        progressCallback.onProgress((seq * 100.0f) / blocks);
        final int blockOffset = seq * blockSize;
        final int remaining = image.length - blockOffset;
        final int thisBlockSize = Math.min(blockSize, remaining);
        // Copy and pad to full blockSize bytes if needed
        final byte[] chunk = new byte[blockSize];
        System.arraycopy(image, blockOffset, chunk, 0, thisBlockSize);
        exchange(FlashDataCommand.builder()
          .sequence(seq)
          .chunk(chunk)
          .build(), timeoutPerMb(Timeout.WRITE_REGION_PER_MB, blockSize));
      }
      progressCallback.onProgress(100.0f);
      progressCallback.onEnd();
    }, t -> progressCallback.onInfo(String.format("Wrote %d bytes at 0x%08X in %.2f seconds (effective %.2f kBit/s)...",
      image.length, flashOffset, t / 1000.0, (image.length * 8) / (t / 1000.0) / 1024.0)));
  }

  public void flashDeflWrite(byte[] image, int blockSize, int flashOffset) {
    progressCallback.onStart();
    progressCallback.onInfo(String.format("Writing %d bytes at 0x%08X...", image.length, flashOffset));
    // Step 1: Compress the data
    final byte[] compressed = compressBytes(image);
    int uncompressedSize = image.length;
    int compressedSize = compressed.length;
    int blocks = (compressedSize + blockSize - 1) / blockSize;
    // Step 2: Send FlashDeflBegin
    time(() -> exchange(FlashDeflBeginCommand.builder()
        .uncompressedSize(isStub ? uncompressedSize : blockSize * blocks)
        .offset(flashOffset)
        .blockSize(blockSize)
        .blocks(blocks)
        .canEncrypt(CHIPS_WITH_FLASH_ENCRYPTION.contains(chipId) && !isStub)
        .build(), timeoutPerMb(Timeout.ERASE_REGION_PER_MB, uncompressedSize))
      , t -> {
        if (!isStub) {
          progressCallback.onInfo(String.format(
            "Took %.2f seconds to erase %d bytes at 0x%08x", t / 1000.0, uncompressedSize, flashOffset)
          );
        }
      });
    // Step 3: Send FlashDeflData blocks
    time(() -> {
      int chunkTimeout = timeoutPerMb(Timeout.WRITE_REGION_PER_MB, blockSize);
      for (int seq = 0; seq < blocks; seq++) {
        progressCallback.onProgress((seq * 100.0f) / blocks);
        int offset = seq * blockSize;
        int end = Math.min(offset + blockSize, compressedSize);
        byte[] chunk = Arrays.copyOfRange(compressed, offset, end);
        exchange(FlashDeflDataCommand.builder()
          .sequence(seq)
          .compressedChunk(chunk)
          .build(), chunkTimeout);
      }
      progressCallback.onProgress(100.0f);
      progressCallback.onEnd();
    }, t -> progressCallback.onInfo(
      String.format("Wrote %d bytes (%d compressed) at 0x%08X in %.2f seconds (effective %.2f kBit/s)...",
        uncompressedSize, compressedSize, flashOffset, t / 1000.0, (uncompressedSize * 8) / (t / 1000.0) / 1024.0)));
  }

  public void flashMd5Verify(byte[] image, int flashOffset) {
    final EspReply reply = exchange(SpiFlashMd5Command.builder()
      .address(flashOffset)
      .size(image.length)
      .build(), timeoutPerMb(Timeout.MD5_PER_MB, image.length));
    final String flashMd5 = isStub
      ? printHex2(Arrays.copyOfRange(reply.getData(), 0, 16))
      : "[" + new String(Arrays.copyOfRange(reply.getData(), 0, 32), StandardCharsets.UTF_8) + "]";
    final String imageMd5 = md5(image);
    if (!imageMd5.equals(flashMd5)) {
      throw new ProtocolFatalException("MD5 hash mismatch: " + flashMd5 + " != " + imageMd5);
    }
  }

  protected void memWrite(byte[] image, int blockSize, int flashOffset) {
    int numBlocks = (image.length + blockSize - 1) / blockSize;
    exchange(MemBeginCommand.builder()
      .size(image.length)
      .blocks(numBlocks)
      .blockSize(blockSize)
      .offset(flashOffset)
      .build(), timeoutPerMb(Timeout.ERASE_REGION_PER_MB, image.length));
    for (int seq = 0; seq < numBlocks; seq++) {
      int offset = seq * blockSize;
      int end = Math.min(offset + blockSize, image.length);
      byte[] chunk = Arrays.copyOfRange(image, offset, end);
      exchange(MemDataCommand.builder()
        .sequence(seq)
        .chunk(chunk)
        .build(), timeoutPerMb(Timeout.WRITE_REGION_PER_MB, blockSize));
    }
  }

  protected void loadStub(byte[] text, int textAdr, byte[] data, int dataAdr, int entryPoint) {
    progressCallback.onInfo(String.format("Loading stub: textAdr=0x%08X, dataAdr=0x%08X, entryPoint=0x%08X", textAdr, dataAdr, entryPoint));
    memWrite(text, MEM_WRITE_SIZE, textAdr);
    memWrite(data, MEM_WRITE_SIZE, dataAdr);
    progressCallback.onInfo(String.format("Executing stub: entryPoint=0x%08X", entryPoint));
    exchange(MemEndCommand.builder()
      .entryPoint(entryPoint)
      .build(), Timeout.COMMAND_SHORT);
    waitForResponse(new byte[]{0x4f, 0x48, 0x41, 0x49}, Timeout.COMMAND_SHORT); // "OH!AI"
    progressCallback.onInfo("Got reply, stub is started");
  }

  public void loadStub() {
    JsonObject json = Json.parse(new String(readResource(chipId.getStubName()), StandardCharsets.UTF_8)).asObject();
    int entryPoint = json.getInt("entry", 0);
    int textAdr = json.getInt("text_start", 0);
    int dataAdr = json.getInt("data_start", 0);
    byte[] text = Base64.getDecoder().decode(json.getString("text", ""));
    byte[] data = Base64.getDecoder().decode(json.getString("data", ""));
    loadStub(text, textAdr, data, dataAdr, entryPoint);
    isStub = true;
  }

  public void espSpiAttach() {
    exchange(SpiAttachCommand.builder()
      .build(), Timeout.COMMAND_SHORT);
  }

  public void setFlashSize(int size) {
    exchange(SpiSetParamsCommand.builder()
      .totalSize(size)
      .build(), Timeout.COMMAND_SHORT);
  }

  public void setBaudRate(int baudRate) {
    exchange(ChangeBaudRateCommand.builder()
      .baudRate(baudRate)
      .build(), Timeout.COMMAND_SHORT);
  }

  public void detectChip() {
    chipId = Esp32ChipId.fromMagicValue(exchange(ReadRegCommand.builder()
      .address(CHIP_DETECT_MAGIC_REG_ADDRESS)
      .build(), Timeout.COMMAND_SHORT).getValue());
    progressCallback.onInfo("Detected chip: " + chipId.getReadableName());
  }

  public void endFlash() {
    exchange(FlashEndCommand.builder()
      .flag(0) // run user code
      .build(), Timeout.COMMAND_SHORT, false);
  }

  public void eraseFlash() {
    if (!isStub) {
      throw new IllegalStateException("FlashEraseCommand is Stub Loader Only command");
    }
    progressCallback.onInfo("Erasing entire flash...");
    exchange(FlashEraseCommand.builder()
      .build(), Timeout.ERASE_REGION_PER_MB * 16);
  }

  public void eraseFlashRegion(int offset, int size) {
    if (!isStub) {
      throw new IllegalStateException("FlashEraseRegionCommand is Stub Loader Only command");
    }
    progressCallback.onInfo(String.format("Erasing flash region: offset=0x%08X, size=%d", offset, size));
    exchange(FlashEraseRegionCommand.builder()
      .offset(offset)
      .size(size)
      .build(), timeoutPerMb(Timeout.ERASE_REGION_PER_MB, size));
  }

  public void readFlash(byte[] dst, int offset, int length) {
    if (!isStub) {
      throw new IllegalStateException("FlashReadCommand is Stub Loader Only command");
    }
    progressCallback.onInfo(String.format("Reading flash region: offset=0x%08X, size=%d", offset, length));
    time(() -> {
      final int blockSize = 0x400;
      final int blockTimeout = timeoutPerMb(Timeout.READ_REGION_PER_MB, blockSize);
      int pos = 0;
      exchange(FlashReadCommand.builder()
        .offset(offset)
        .size(length)
        .blockSize(blockSize)
        .inFlightBlocks(2)
        .build(), Timeout.COMMAND_SHORT);
      while (pos < length) {
        byte[] chunk = waitForResponse(null, blockTimeout);
        System.arraycopy(chunk, 0, dst, pos, chunk.length);
        pos += chunk.length;
        ByteBuffer payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(pos);
        byte[] pkt = slipEncode(payload.array());
        serialTransport.write(pkt, pkt.length);
        log.trace(">>>>: {}: {}", pkt.length, printHex(pkt));
        progressCallback.onProgress((pos * 100.0f) / length);
      }
      String flashMd5 = printHex2(waitForResponse(null, timeoutPerMb(Timeout.READ_REGION_PER_MB, blockSize)));
      String imageMd5 = md5(Arrays.copyOf(dst, length));
      if (!imageMd5.equals(flashMd5)) {
        throw new ProtocolFatalException("MD5 hash mismatch: " + flashMd5 + " != " + imageMd5);
      }
    }, t -> {
      progressCallback.onProgress(100.0f);
      progressCallback.onEnd();
      progressCallback.onInfo(String.format("Read %d bytes at 0x%08X in %.2f seconds (effective %.2f kBit/s)...",
        length, offset, t / 1000.0, (length * 8) / (t / 1000.0) / 1024.0));
    });
  }

  public void sync() {
    byte[] pkt = slipEncode(SyncCommand.builder().build().toPacket().getPacket());
    for (int i = 0; i < 20; i++) {
      serialTransport.write(pkt, pkt.length);
      try {
        EspReply res = waitForResponse(RomCommand.SYNC, Timeout.SYNC);
        if (res.isSuccess(false)) {
          // flush the port to remove any pending replies
          while (true) {
            try {
              waitForResponse(RomCommand.SYNC, Timeout.SYNC);
            } catch (CommandTimeoutException e) {
              return;
            }
          }
        }
      } catch (CommandTimeoutException ignored) {
      }
    }
    throw new ProtocolFatalException("Failed to sync with ESP chip");
  }

  public void reset() {
    serialTransport.setControlLines(false, false);
    delayMS(100);
    serialTransport.setControlLines(false, true);
    delayMS(100);
    serialTransport.setControlLines(false, false);
  }

  public void enterBootLoader() {
    serialTransport.setControlLines(true, false);
    delayMS(100);
    serialTransport.setControlLines(false, true);
    delayMS(100);
    serialTransport.setControlLines(true, false);
  }

  public void setProgressCallback(@Nonnull ProgressCallback progressCallback) {
    this.progressCallback = progressCallback;
  }
}
