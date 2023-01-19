package xyz.hydar.net;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
abstract class Decoder{//package-private
	public final Charset ch;
	public boolean skipLast=false;
	public boolean skipLn=false;
	public int fixedLen;
	public final boolean utf16BOM;
	public final boolean utf32BOM;
	Decoder() {
		this(StandardCharsets.ISO_8859_1);
	}
	Decoder(Charset ch) {
		this.ch=ch;
		var encoder=ch.newEncoder();
		utf16BOM=Objects.equals(ch,StandardCharsets.UTF_16)||ch.name().equalsIgnoreCase("x-UTF-16LE-BOM")||ch.name().equalsIgnoreCase("x-UTF-16BE-BOM");
		utf32BOM=Objects.equals(ch.name(),"X-UTF-32LE-BOM")||ch.name().equalsIgnoreCase("X-UTF-32BE-BOM");
		if(encoder.maxBytesPerChar()==encoder.averageBytesPerChar()) {
			fixedLen=Math.round(encoder.maxBytesPerChar());
		}else fixedLen=0;
	}
	private static class U32Holder{
		private static final Charset le;
		private static final Charset be;
		static {
			le=Charset.forName("UTF-32LE");
			be=Charset.forName("UTF-32BE");
		}
	}
	public Charset ch(boolean bigEndian) {
		if(utf16BOM) {
			return bigEndian?StandardCharsets.UTF_16BE:StandardCharsets.UTF_16LE;
		}else if(utf32BOM) {
			return bigEndian?U32Holder.be:U32Holder.le;
		}return ch;
	}
	public void reset() {
		skipLast=skipLn=false;
	}
	public static Decoder newInstance(Charset ch, char delimiter) {
		return new OfChar(ch,delimiter);
	}
	public static Decoder newInstance(Charset ch, char... delimiters) {
		return delimiters.length<4?new OfChars(ch, delimiters):new OfSortedChars(ch,delimiters);
	}
	protected abstract boolean isDelimChar(char c);
	static class OfChar extends Decoder{
		final char delim;
		public OfChar(Charset ch, char delimiter) {
			super(ch);
			this.delim=delimiter;
		}
		@Override
		protected boolean isDelimChar(char c) {
			return delim==c;
		}
	}
	static class OfChars extends Decoder{
		final char[] delimiters;
		public OfChars(Charset ch, char[] delimiters) {
			super(ch);
			this.delimiters=delimiters;
		}
		@Override
		protected boolean isDelimChar(char c) {
			for(char d:delimiters)
				if(c==d)return true;
			return false;
		}
	}
	static class OfSortedChars extends Decoder{
		final char[] delimiters;
		public OfSortedChars(Charset ch,char[] delimiters) {
			super(ch);
			this.delimiters=Arrays.copyOf(delimiters,delimiters.length);
			Arrays.sort(this.delimiters);
		}
		@Override
		protected boolean isDelimChar(char c) {
			return Arrays.binarySearch(delimiters,c)>=0;
		}
	}
	static class OfChunks extends OfLines{
		public OfChunks(Charset ch) {
			super(ch);
		}
		@Override
		protected boolean ln(char c){
			return c=='\0'||c=='\r'||c=='\n';
		}
	}
	static class OfLines extends Decoder{
		public OfLines(Charset ch) {
			super(ch);
		}
		protected boolean ln(char c) {
			return c=='\r'||c=='\n';
		}
		@Override
		protected boolean isDelimChar(char c) {
			if(skipLn) {
				skipLn=false;
				if(c=='\n') {
					skipLast=true;
					return true;
				}
			}
			if(c=='\r') {
				skipLn=true;
			}return ln(c);
		}
	}
}
/**Client context that can read delimited bytes in (almost) any charset. All charsets in StandardCharsets are supported, as well as most others*.<br>
 * Only onMessage() needs to be overridden.<br>
 * If no delimiters are specified, only the end of stream or finishRead() will trigger onMessage().<br>
 * Reading in UTF-32 BOM requires the non-BOM utf32 charsets to be available.<br> 
 * *If the charset requires a BOM and isn't UTF-16, UTF-32BE-BOM, or UTF-32LE-BOM, it will most likely fail.
 * */
public abstract class TextClientContext extends ClientContext {
	private Decoder decoder;
	private boolean reading=false;
	private boolean eos=false;
	private static final char[] EMPTY=new char[0];
	public TextClientContext(Decoder decoder){
		super();
		this.decoder=decoder;
	}
	TextClientContext(Decoder decoder, ClientOptions options){
		super(options);
		this.decoder=decoder;
	}
	public TextClientContext(char delimiter) {
		this(StandardCharsets.ISO_8859_1,delimiter);
	}
	public TextClientContext(char[] delimiters) {
		this(StandardCharsets.ISO_8859_1,delimiters);
	}
	public TextClientContext(Charset ch, char delimiter) {
		super();
		decoder=Decoder.newInstance(ch,delimiter);
	}
	public TextClientContext(Charset ch, char[] delimiters) {
		super();
		decoder=Decoder.newInstance(ch,delimiters);
	}
	public TextClientContext(Charset ch, char delimiter,ClientOptions options) {
		super(options);
		decoder=Decoder.newInstance(ch,delimiter);
	}
	public TextClientContext(Charset ch, char[] delimiters,ClientOptions options) {
		super(options);
		decoder=Decoder.newInstance(ch,delimiters);
	}
	/**Remove all delimiters. onMessage will be called only by finishRead().*/
	public void noDelimiters() throws IOException {
		decoder=Decoder.newInstance(decoder.ch,EMPTY);
	}
	/**Change the delimiters.*/
	public void setDelimiter(char delimiter) throws IOException {
		decoder=Decoder.newInstance(decoder.ch,delimiter);
	}
	/**Change the delimiters.*/
	public void setDelimiters(char[] delimiters) throws IOException {
		decoder=Decoder.newInstance(decoder.ch,delimiters);
	}
	/**Called when a delimited string, msg, is read.*/
	public abstract void onMessage(String msg) throws IOException;
	private int primaryOffset;
	/**Call to read the remaining characters as a delimited block. Can be used before switching to a binary protocol.<br>
	 * No line indices or terminator checks are retained after calls.*/
	public void finishRead() throws IOException{
		if(reading)eos=true;
		else onData(client.buffer(),-1);
	}
	/**Calls onMessage on delimited blocks of the provided buffer.*/
	@Override
	public void onData(ByteBuffer data, int length) throws IOException{
		reading=true;
		data.flip();
		Charset ch=decoder.ch;
		ByteBuffer oldData=null;
		if(decoder.utf16BOM&&data.remaining()>=2) {
			short bom=data.getShort();
			oldData=data;
			data=data.slice(2,data.limit()-2);
			ch=decoder.ch((bom!=(short)0xFFFE));
		}else if(decoder.utf32BOM&&data.remaining()>=4) {
			int bom=data.getInt();
			oldData=data;
			data=data.slice(4,data.limit()-4);
			ch=decoder.ch((bom!=0xFFFE0000));
		}
		int byteOffset=data.limit();
		int start=0;
		boolean oneByte=decoder.fixedLen==1;
		CharBuffer charData;
		Buffer primary=data;
		if(!oneByte) {
			charData=ch.decode(data);
			primary=charData;
		}else charData=null;
		primary.position(primaryOffset);
		primaryOffset=0;
		while(start<primary.limit()) {
			boolean endLine=false;
			if(!oneByte)
				while(charData.hasRemaining()&&!(endLine=decoder.isDelimChar(charData.get())));
			else while(data.hasRemaining()&&!(endLine=decoder.isDelimChar((char)data.get())));
			if(endLine||length<0) {
				int len=primary.position()-start;
				String str;
				if(!(decoder.skipLast&&len<=1)) {
					int size=decoder.skipLast?len-2:len-1;
					if(length<0&&!primary.hasRemaining()&&!endLine)
						size++;
					str=oneByte?(data.hasArray()?new String(data.array(),start,size,ch):ch.decode(data.slice(start,size)).toString())
							:charData.slice(start,size).toString();
					onMessage(str);
					if(eos)length=-1;
				}
				start+=len;
				decoder.skipLast=false;
			}
			else break;
		}
		Buffer end=primary.slice(start,primary.limit()-start);
		int byteLen=(decoder.fixedLen>0)?
			end.limit()*decoder.fixedLen:
			end.toString().getBytes(ch).length;
		primaryOffset=primary.limit()-start;
		if(length<0) {
			primaryOffset=0;
			decoder.reset();
		}
		data.position(byteOffset-byteLen).compact().position(byteLen);
		if(oldData!=null) {
			oldData.position(data.position()+(decoder.utf16BOM?2:4));
		}
		eos=false;
		reading=false;
	}
	/**Sends the given string.*/
	public void send(String msg) throws IOException{
		send(msg.getBytes(decoder.ch));
	}
	/**Sends the given string with \n appended to it.*/
	public void sendln(String msg) throws IOException{
		send((msg+"\n").getBytes(decoder.ch));
	}
}
