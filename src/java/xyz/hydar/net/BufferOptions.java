package xyz.hydar.net;

/**Buffer options, part of ClientOptions.<br>
 * min/max: Minimum and maximum buffer size. Can be further controlled through overriding functions in ClientContext. Default 1024; 0 indicates not present.<br>
 * direct: Whether the buffer should be created using allocateDirect.<br>
 * locked: Whether this buffer should be synchronized. Only applies to output.<br>
 * */
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
	/**Load from a string. Examples: <br>
	 * 1024,1024,direct,locked<br>
	 * 1024,8192,locked*/
	public static BufferOptions from(String src) {
		return from(src,DEFAULT);
	}
	/**Load from a string, with default values from {@code defaults}. Examples: <br>
	 * 1024,1024,direct,locked<br>
	 * 1024,8192,locked*/
	public static BufferOptions from(String src, BufferOptions defaults) {
		src=src.trim().toLowerCase();
		if(src.startsWith("(")&&src.endsWith(")"))
			src=src.substring(1,src.length()-1);
		String[] cmds=src.split(",");
		boolean direct=defaults.direct;
		boolean locked=defaults.locked;
		int min=defaults.min,max=defaults.max;
		int idx=0;
		for(String cmd:cmds) {
			cmd=cmd.trim();
			if(cmd.isEmpty())continue;
			else if(cmd.equals("direct"))
				direct=true;
			else if(cmd.equals("locked"))
				locked=true;
			else if(idx==0)min=Integer.parseInt(cmd);
			else if(idx==1)max=Integer.parseInt(cmd);
			idx++;
		}
		return new BufferOptions(min,max,direct,locked);
	}
};