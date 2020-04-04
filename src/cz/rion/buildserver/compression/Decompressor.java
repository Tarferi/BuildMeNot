package cz.rion.buildserver.compression;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.compression.BitStream.BitStreamWriter;
import cz.rion.buildserver.exceptions.CompressionException;

public class Decompressor {

	private static final DecompressionAlgorithm[] Algorithms = new DecompressionAlgorithm[] { new GzipDecompressor(), new DeflateDecompressor() };

	private static interface DecompressionAlgorithm {
		byte[] decompress(byte[] data, int uncompressedLength) throws CompressionException;
	}

	private static int readInt(byte[] buf, int position) {
		return ((buf[position + 0] & 0xff) << 24) | ((buf[position + 1] & 0xff) << 16) | ((buf[position + 2] & 0xff) << 8) | (buf[position + 3] & 0xff) & 0xffffffff;
	}

	private static final class GzipDecompressor implements DecompressionAlgorithm {

		@Override
		public byte[] decompress(byte[] data, int uncompressedLength) throws CompressionException {
			GZIPInputStream gzip = null;
			ByteArrayInputStream in = null;
			try {
				in = new ByteArrayInputStream(data);
				gzip = new GZIPInputStream(in);
				byte[] result = new byte[uncompressedLength];
				int read = gzip.read(result);
				while (read != result.length) {
					int readNow = gzip.read(result, read, result.length - read);
					if (readNow <= 0) {
						throw new CompressionException("Failed to read compressed data");
					}
					read += readNow;
				}
				return result;
			} catch (IOException e) {
				throw new CompressionException("Decompression failed on " + data.length + " bytes", e);
			} finally {
				try {
					gzip.close();
				} catch (Exception e) {
				}
				try {
					in.close();
				} catch (Exception e) {
				}
			}
		}

	}

	private static final class DeflateDecompressor implements DecompressionAlgorithm {

		@Override
		public byte[] decompress(byte[] data, int uncompressedLength) throws CompressionException {
			Inflater decompresser = null;
			try {
				decompresser = new Inflater();
				decompresser.setInput(data);

				byte[] result = new byte[uncompressedLength];
				int read = decompresser.inflate(result);
				while (read != result.length) {
					int readNow = decompresser.inflate(result, read, result.length - read);
					if (readNow < 0) {
						throw new CompressionException("Failed to read compressed data");
					} else if (readNow == 0) {
						break;
					}
					read += readNow;
				}
				if (read != result.length) {
					throw new CompressionException("Failed to read compressed data");
				}
				decompresser.end();
				return result;
			} catch (Exception e) {
				throw new CompressionException("Failed to inflate", e);
			} finally {
				try {
					decompresser.end();
				} catch (Exception e) {
				}
			}
		}

	}

	private static byte[] decode(String encodedData) throws CompressionException {
		BitStream.BitStreamWriter bs = new BitStreamWriter(6);
		for (char c : encodedData.toCharArray()) {
			byte b = (byte) c;
			if (b >= BitStream.decodingTable.length) {
				throw new CompressionException("Decoding failed (" + b + ")?");
			}
			int value = BitStream.decodingTable[b];
			bs.write(value);
		}
		byte[] data = bs.get(4);
		int len = readInt(data, 0);
		data = bs.get(4 + len);
		byte[] result = new byte[len];
		System.arraycopy(data, 4, result, 0, result.length);
		return result;
	}

	public static String decompress(String dataStr) throws CompressionException {
		return decompress(dataStr, 1);
	}

	public static String decompress(String dataStr, int levels) throws CompressionException {
		if (dataStr.isEmpty()) {
			return "";
		}
		if (dataStr.startsWith("0")) {
			return dataStr.substring(1);
		} else if (dataStr.startsWith("1")) {
			dataStr = dataStr.substring(1);
		} else {
			throw new CompressionException("Invalid format: " + dataStr.substring(0, 1));
		}

		byte[] data = decode(dataStr);
		for (int compressionLevel = 0; compressionLevel < levels; compressionLevel++) {
			if (data.length == 0) {
				return "";
			}
			if (data[0] == -1) { // Not encoded
				byte[] rawData = new byte[data.length - 1];
				System.arraycopy(data, 1, rawData, 0, rawData.length);
				data = rawData;
			} else if (data[0] >= 0 && data[0] < Algorithms.length) { // GZIP

				byte[] compressedData = new byte[data.length - 5];
				int uncompressedLength = readInt(data, 1);
				System.arraycopy(data, 5, compressedData, 0, compressedData.length);

				byte[] decompressedData = Algorithms[data[0]].decompress(compressedData, uncompressedLength);
				data = decompressedData;
			} else {
				throw new CompressionException("Invalid compression algorithm: " + data[0]);
			}
		}
		return new String(data, Settings.getDefaultCharset());
	}
}
