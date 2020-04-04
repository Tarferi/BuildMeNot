package cz.rion.buildserver.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.compression.BitStream.BitStreamReader;
import cz.rion.buildserver.exceptions.CompressionException;

public class Compressor {

	private static final CompressionAlgorithm[] Algorithms = new CompressionAlgorithm[] { new GzipCompressor(), new DeflateCompressor() };

	private static void writeInt(byte[] output, int outputPosition, int value) {
		output[outputPosition + 0] = (byte) ((value >> 24) & 0xff);
		output[outputPosition + 1] = (byte) ((value >> 16) & 0xff);
		output[outputPosition + 2] = (byte) ((value >> 8) & 0xff);
		output[outputPosition + 3] = (byte) (value & 0xff);
	}

	private static String encode(byte[] binary) throws CompressionException {
		byte[] withLength = new byte[binary.length + 4];
		System.arraycopy(binary, 0, withLength, 4, binary.length);
		StringBuilder sb = new StringBuilder();
		writeInt(withLength, 0, binary.length);

		BitStream.BitStreamReader bs = new BitStreamReader(withLength, 6);
		while (bs.hasNext()) {
			int b = bs.getNext();
			if (b >= BitStream.encodingTable.length) {
				throw new CompressionException("Encoding failed (" + b + ")?");
			}
			sb.append(BitStream.encodingTable[b]);
		}

		return sb.toString();
	}

	private static final class GzipCompressor implements CompressionAlgorithm {

		public byte[] compress(byte[] data, int margin) throws CompressionException {
			GZIPOutputStream gzip = null;
			ByteArrayOutputStream out = null;
			try {
				out = new ByteArrayOutputStream();
				gzip = new GZIPOutputStream(out);
				gzip.write(data);
				gzip.close();
				out.close();
				byte[] compressedData = out.toByteArray();
				if (compressedData.length + margin >= data.length) { // Not worth it
					return null;
				}
				byte[] newResult = new byte[compressedData.length + margin];
				System.arraycopy(compressedData, 0, newResult, margin, compressedData.length);
				return newResult;
			} catch (IOException e) {
				throw new CompressionException("Compression failed on " + data.length + " bytes", e);
			} finally {
				try {
					gzip.close();
				} catch (Exception e) {
				}
				try {
					out.close();
				} catch (Exception e) {
				}
			}
		}

	}

	private static final class DeflateCompressor implements CompressionAlgorithm {

		public byte[] compress(byte[] data, int margin) throws CompressionException {
			Deflater compresser = null;
			try {
				compresser = new Deflater(Deflater.BEST_COMPRESSION);
				compresser.setInput(data);
				compresser.finish();

				byte[] result = new byte[(data.length * 2) + margin];
				int compressedDataLength = compresser.deflate(result, margin, result.length - margin);
				while (compressedDataLength != result.length - margin) {
					int compressedNow = compresser.deflate(result, compressedDataLength + margin, result.length - compressedDataLength - margin);
					if (compressedNow == 0) {
						break;
					} else if (compressedNow < 0) { // Error?
						return null;
					} else {
						compressedDataLength += compressedNow;
					}
				}
				compresser.end();
				if (compressedDataLength < 0 || compressedDataLength >= result.length) {
					return null;
				}
				if (compressedDataLength != result.length) {
					byte[] newResult = new byte[compressedDataLength + margin];
					System.arraycopy(result, 0, newResult, 0, newResult.length);
					return newResult;
				} else {
					return result;
				}
			} catch (Exception t) {
				throw new CompressionException("Failed to compress using DEFLATE", t);
			} finally {
				try {
					compresser.end();
				} catch (Exception e) {
				}
			}
		}

	}

	private static interface CompressionAlgorithm {
		byte[] compress(byte[] data, int margin) throws CompressionException;
	}

	public static String compress(String input) throws CompressionException {
		return compress(input, 1);
	}

	public static String compress(String input, int levels) throws CompressionException {
		if (input.isEmpty()) {
			return "0";
		}
		byte[] data = input.getBytes(Settings.getDefaultCharset());
		for (int compressionLevel = 0; compressionLevel < levels; compressionLevel++) {
			byte[][] ratios = new byte[Algorithms.length][];

			// Pick a compression
			for (int i = 0; i < Algorithms.length; i++) {
				ratios[i] = Algorithms[i].compress(data, 5);
			}

			// Find the best compression
			byte[] best = null;
			int compressorIndex = 0;
			for (int i = 0; i < Algorithms.length; i++) {
				byte[] compressed = ratios[i];
				if (compressed != null) {
					if (best == null) {
						best = compressed;
						compressorIndex = i;
					} else if (best.length > compressed.length) {
						best = compressed;
						compressorIndex = i;
					}
				}
			}

			byte[] result;
			if (best == null) { // No compression can be better than what it is now
				result = new byte[data.length + 1];
				System.arraycopy(data, 0, result, 1, data.length);
				result[0] = -1; // Magic constant
			} else { // At least one compression can be done
				best[0] = (byte) (compressorIndex & 0xff);
				writeInt(best, 1, data.length);
				result = best;
			}
			data = result;
		}
		String result = encode(data);
		if (result.length() > input.length()) {
			return "0" + input;
		} else {
			return "1" + result;
		}
	}

}
