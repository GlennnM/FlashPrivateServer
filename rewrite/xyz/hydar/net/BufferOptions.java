package xyz.hydar.net;
/**Buffer options, part of ClientOptions. Note that locked input doesn't actually mean anything*/
public record BufferOptions(int min, int max, boolean direct, boolean locked) {
	public static final BufferOptions DEFAULT = new BufferOptions(1024,1024,false,false);
	public static final BufferOptions NONE = new BufferOptions(0,0,false,false);
	public BufferOptions{
		if(min>max)throw new IllegalArgumentException(""+min+" > "+max);
	}
	public BufferOptions(int fixedSize) {
		this(fixedSize,fixedSize,DEFAULT.direct,DEFAULT.locked);
	}
	public BufferOptions(int min, int max) {
		this(min,max,DEFAULT.direct,DEFAULT.locked);
	}
};