package xyz.hydar.bmc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.json.JSONObject;
import org.json.JSONTokener;

public class Compressors {
	public final Map<String, UnaryOperator<ByteBuffer>> decompressors = new HashMap<>();
	public final UnaryOperator<ByteBuffer> compressor;
	public static final int MODE = 0;//0 = prefer zstd jni 1 = prefer zstd java 2 = prefer gzip
	public static final int MAX_BUFFER=10_024_000;
	public static final boolean USE_DICT=true;
	public Compressors(Path dict) {
		UnaryOperator<ByteBuffer> tmpCompressor = null;
		// decompress needs to check magic byte
		try {
			Class<?> zd = Class.forName("com.github.luben.zstd.ZstdDictCompress");
			Class<?> zdd = Class.forName("com.github.luben.zstd.ZstdDictDecompress");
			Object zdc, zddd;
			if (Files.exists(dict)) {
				byte[] d = Files.readAllBytes(dict);
				zdc = zd.getDeclaredConstructor(byte[].class, int.class).newInstance(d, 3);
				zddd = zdd.getDeclaredConstructor(byte[].class).newInstance(d);
			} else {
				zdc = null;
				zddd = null;
			}
			Class<?> z = Class.forName("com.github.luben.zstd.Zstd");
			var zl = z.getDeclaredMethod("compressBound", long.class);
			var zc = z.getDeclaredMethod("compressFastDict", byte[].class,int.class, byte[].class,int.class,int.class, zd);
			var zc2 = z.getDeclaredMethod("compressByteArray", byte[].class, int.class,int.class,byte[].class, int.class, int.class, int.class);
			var zde = z.getDeclaredMethod("decompress", byte[].class, zdd, int.class);
			var zde2 = z.getDeclaredMethod("decompress", byte[].class);
			var fcs = z.getDeclaredMethod("getFrameContentSize", byte[].class);
			tmpCompressor = x -> {
				if (x.limit() == 0)
					return x;
				// -->just remove the 'start with z' thing since theres magic number
				try {
					int byteLen = (int) (long) zl.invoke(null, x.limit());
					var output = new byte[byteLen];
					long w = (Long) ((zdc != null && USE_DICT) ? zc.invoke(null, output, 0, x.array(), 0, x.limit(), zdc) :
						zc2.invoke(null, output, 0, byteLen, x.array(), 0,x.limit(), 3));
					if (w >= x.limit())
						return x;
					return ByteBuffer.wrap(output, 0, (int) w);
				} catch (IllegalAccessException | InvocationTargetException e) {
					return x;
				}
			};
			this.decompressors.put("z", x -> {
				try {
					if(x.array().length != x.limit())
						throw new IllegalArgumentException("decompress needs all bytes");
					int byteLen = (int) (long) fcs.invoke(null, x.array());
					if (byteLen >= MAX_BUFFER)
						throw new RuntimeException("Compressed file >10MB");
					return ByteBuffer
							.wrap((byte[]) (zdc != null ? zde.invoke(null, x.array(), zddd, byteLen) : zde2.invoke(null,x.array())));
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (Throwable cfe) {
			System.out.println("Skipping zstd-jni (requires --enable-native-access=ALL_UNNAMED if jdk 22+)");
		}
		try {
			Class<?> zc = Class.forName("io.airlift.compress.v3.zstd.ZstdCompressor");
			Class<?> zdc = Class.forName("io.airlift.compress.v3.zstd.ZstdDecompressor");
			Method m = zc.getDeclaredMethod("create");
			var comp = m.invoke(null);
			Method m2 = zdc.getDeclaredMethod("create");
			var decomp = m2.invoke(null);
			var compress = zc.getMethod("compress", byte[].class, int.class, int.class, byte[].class, int.class,
					int.class);
			var decompress = zdc.getMethod("decompress", byte[].class, int.class, int.class, byte[].class, int.class,
					int.class);
			var gds = zdc.getMethod("getDecompressedSize", byte[].class, int.class, int.class);
			var zl = zc.getMethod("maxCompressedLength", int.class);
			if(tmpCompressor == null || MODE==1) {
					tmpCompressor = x -> {
						if (x.limit() == 0)
							return x;
						// -->just remove the 'start with z' thing since theres magic number
						// this copies anyways --> use byte buffer
						try {
							int byteLen = (int) zl.invoke(comp, x.limit());
							var output = new byte[byteLen];
							int w = (int) compress.invoke(comp, x.array(), 0, x.limit(), output, 0, byteLen);
							return ByteBuffer.wrap(output, 0, w);
						} catch (IllegalAccessException | InvocationTargetException e) {
							return x;
						}
					};
				this.decompressors.put("z", x -> {
					if(x.array().length != x.limit())
						throw new IllegalArgumentException("decompress needs all bytes");
					try {
						int byteLen = (int)(long) gds.invoke(decomp, x.array(), 0, x.limit());
						if (byteLen >= MAX_BUFFER)
							throw new RuntimeException("Compressed file >10MB");
						byte[] output = new byte[byteLen];
						int w = (int) decompress.invoke(decomp, x.array(), 0, x.limit(), output, 0, byteLen);
						return ByteBuffer.wrap(output, 0, w);
					} catch (IllegalAccessException | InvocationTargetException e) {
						throw new RuntimeException(e);
					}
				});
			}
		} catch (Throwable cfe) {
			System.out.println("Skipping Airlift zstd (requires jdk 25 and aircompressor lib added) - "+cfe);
		}
		if (tmpCompressor == null || MODE == 2) {
			System.out.println("Compressor: deflate");
			tmpCompressor = x -> {
				byte[] output = new byte[x.limit() * 3 / 4];
				int w = 0;
				var def = new Deflater();
				try {
					def.setInput(x);
					def.finish();
					w = def.deflate(output);
				} finally {
					def.end();
				}
				if (w == output.length || w <= 0)
					return x;
				else
					return ByteBuffer.wrap(output, 0, w);
			};
		}
		this.decompressors.put("g", x -> {
			byte[] output = null;
			int w = 0, t = 0;
			int byteLength = x.limit() * 4;
			var inf = new Inflater();
			try {
				inf.setInput(x);
				while (!inf.finished() && t < MAX_BUFFER) {
					output = output == null ? new byte[byteLength] : Arrays.copyOf(output, byteLength);
					t += (w = inf.inflate(output, t, byteLength - t));
					byteLength *= 2;
				}
				if (t >= MAX_BUFFER)
					throw new RuntimeException("Compressed file >10MB");
			} catch (DataFormatException e) {
				throw new RuntimeException(e);
			} finally {
				inf.end();
			}
			return ByteBuffer.wrap(output, 0, t);
		});
		this.compressor = tmpCompressor;	
	}
	public void write(JSONObject j, Path p) throws IOException {
		var b = new ByteArrayOutputStream() {public byte[] buf() { return super.buf;}};
		try(var w = new OutputStreamWriter(b);){
			j.write(w);
		}
		try(var f = Files.newOutputStream(p)){
			var comp = compressor.apply(ByteBuffer.wrap(b.buf(), 0, b.size()));
			f.write(comp.array(), 0, comp.limit());
		}
	}
	public JSONObject read(Path p) throws IOException {
		var input = Files.readAllBytes(p);
		if(input.length==0)return null;
		var alg = switch(input[0]) {
			case 0x78, 0x1f->"g";
			case 0x28 -> "z";
			default->"";
		};
		var dec = decompressors.get(alg);
		if(alg.isEmpty())
			return new JSONObject(new String(input, StandardCharsets.ISO_8859_1));
		else {
			var out = decompressors.get(alg).apply(ByteBuffer.wrap(input));
			return new JSONObject(new JSONTokener(new ByteArrayInputStream(out.array(), 0, out.limit())));
		}
	}
}
