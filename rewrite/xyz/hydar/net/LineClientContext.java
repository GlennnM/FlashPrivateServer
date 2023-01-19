package xyz.hydar.net;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
/**Client context that can read lines(ending with \r\n or \r or \n) efficiently in any charset, calling onMessage.<br>
 * Only onMessage() needs to be overriden, but for multiplexing binary and text data onData() can be overriden as well.<br>
 * Leftover bytes won't be consumed until finishRead() is called. Adding finishRead() to onClose() will ensure they are made into a line.<br>
 * Null(\0) can also be specified as a delimiter.
 * These can even be used outside of a server context to read lines from any ByteBuffer, <br>
 * by calling onData and finishRead. Heap buffers are preferred since strings are always in Java heap space.
 * */
public abstract class LineClientContext extends TextClientContext{

	public LineClientContext() {
		this(StandardCharsets.ISO_8859_1,false);
	}
	public LineClientContext(Charset ch) {
		this(ch,false);
	}
	public LineClientContext(ClientOptions options) {
		this(StandardCharsets.ISO_8859_1,false,options);
	}
	public LineClientContext(Charset ch, ClientOptions options) {
		this(ch,false,options);
	}
	public LineClientContext(Charset ch, boolean nullTerminates) {
		super(nullTerminates?new Decoder.OfChunks(ch):new Decoder.OfLines(ch));
	}
	public LineClientContext(Charset ch, boolean nullTerminates, ClientOptions options) {
		super(nullTerminates?new Decoder.OfChunks(ch):new Decoder.OfLines(ch),options);
	}
}