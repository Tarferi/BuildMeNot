package cz.rion.buildserver.compression;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.exceptions.CompressionException;

public class BitStream {

	static final char[] encodingTable = new char[] {
			'A',
			'B',
			'C',
			'D',
			'E',
			'F',
			'G',
			'H',
			'I',
			'J',
			'K',
			'L',
			'M',
			'N',
			'O',
			'P',
			'Q',
			'R',
			'S',
			'T',
			'U',
			'V',
			'W',
			'X',
			'Y',
			'Z',

			'a',
			'b',
			'c',
			'd',
			'e',
			'f',
			'g',
			'h',
			'i',
			'j',
			'k',
			'l',
			'm',
			'n',
			'o',
			'p',
			'q',
			'r',
			's',
			't',
			'u',
			'v',
			'w',
			'x',
			'y',
			'z',

			' ',
			'!',
			'"',
			'#',
			'$',
			'%',
			'&',
			'\'',
			'(',
			')',
			'*',
			'+',
			',',
			'-',
			'.',
			'/',
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			':',
			';',
			'<',
			'=',
			'>',
			'?',
			'@',
			'[',
			'\\',
			']',
			'^',
			'_',
			'`',
			'{',
			'|',
			'}',
			'~'
	};

	static final int[] decodingTable = initDecodingTable();

	private static int[] initDecodingTable() {
		int[] tbl = new int[255];
		for (int i = 0; i < tbl.length; i++) {
			tbl[i] = -1;
		}
		int index = 0;
		for (char c : encodingTable) {
			byte b = (byte) c;
			tbl[b] = index;
			index++;
		}
		return tbl;
	}

	static class BitStreamWriter extends SimpleBitStreamWriter {

		private int itemSize;

		public BitStreamWriter(int itemSize) {
			this.itemSize = itemSize;
		}

		public void write(int value) {
			for (int bitIndex = itemSize - 1; bitIndex >= 0; bitIndex--) {
				int bit = (value & (1 << bitIndex)) == 0 ? 0 : 1;
				this.putBit(bit);
			}
		}

	}

	static class BitStreamReader extends SimpleBitStreamReader {

		private int readSoFar = 0;
		private final int totalItems;
		private int itemSize;
		private final int maxValue;

		public BitStreamReader(byte[] array, int itemSize) {
			super(array);
			this.totalItems = ((array.length * 8) / itemSize) + ((array.length * 8) % 6 == 0 ? 0 : 1);
			this.itemSize = itemSize;

			int max = 1;
			for (int i = 0; i < itemSize - 1; i++) {
				max <<= 1;
				max |= 1;
			}
			maxValue = max;
		}

		public boolean hasNext() {
			return readSoFar < totalItems;
		}

		public int getNext() throws CompressionException {
			readSoFar++;
			int val = 0;
			for (int i = 0; i < itemSize; i++) {
				val <<= 1;
				val |= getNextBit();
			}
			if (val > maxValue) {
				throw new CompressionException("Bit stream reader failed");
			}
			return val;
		}

	}

	static class SimpleBitStreamWriter {

		private final List<Byte> data = new ArrayList<>();
		private int bitPos = 0;
		private byte lastByte;

		public SimpleBitStreamWriter() {
			lastByte = 0;
		}

		public byte[] get(int totalBytes) {
			byte[] result = new byte[totalBytes];
			int index = 0;
			for (Byte b : data) {
				if (index == totalBytes) {
					break;
				}
				result[index] = b;
				index++;
			}
			if (index != totalBytes) {
				result[index] = lastByte;
			}
			return result;
		}

		public void putBit(int bit) {
			lastByte <<= 1;
			lastByte |= bit == 0 ? 0 : 1;
			if (bitPos == 7) {
				data.add(lastByte);
				lastByte = 0;
				bitPos = 0;
			} else {
				bitPos++;
			}
		}

	}

	static class SimpleBitStreamReader {

		private final byte[] array;
		private int arrayPos = 0;
		private int bitPos = 0;

		public SimpleBitStreamReader(byte[] array) {
			this.array = array;
		}

		public int getNextBit() {
			byte currentByte = arrayPos < array.length ? array[arrayPos] : 0;
			int bitIndex = 7 - bitPos;
			int value = currentByte & (1 << bitIndex);
			if (bitPos == 7) {
				arrayPos++;
				bitPos = 0;
			} else {
				bitPos++;
			}
			return value == 0 ? 0 : 1;
		}

	}
}
