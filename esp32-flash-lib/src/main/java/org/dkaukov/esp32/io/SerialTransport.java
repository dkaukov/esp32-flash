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

public interface SerialTransport {
  int read(byte[] buffer, int length) throws IOException;
  void write(byte[] buffer, int length) throws IOException;
  void setControlLines(boolean dtr, boolean rts) throws IOException;
  default int readBufferSize() {return 64;}
}
