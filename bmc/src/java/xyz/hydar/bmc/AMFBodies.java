package xyz.hydar.bmc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.openamf.AMFBody;
import org.openamf.AMFMessage;
import org.openamf.io.AMFDeserializer;

public class AMFBodies extends ArrayList<AMFBody> {
	private static final long serialVersionUID = 9115120997806482132L;
	private AMFBodies(AMFMessage msg) {
		super(IntStream.range(0,msg.getBodyCount())
				.mapToObj(msg::getBodyAt)
				.sorted(Comparator.comparing(AMFBody::getResponse))
				.toList());
	}

	public static AMFBodies from(AMFMessage msg) {
		return new AMFBodies(msg);
	}

	public static AMFBodies from(InputStream is) throws IOException {
		//PrintStream old = System.out;
		return new AMFBodies(new AMFDeserializer(new DataInputStream(is)).getAMFMessage());
	}

	public static AMFBodies from(String filePath) throws IOException {
		return from(Files.newInputStream(Paths.get(filePath)));
	}

	public static AMFBodies fromHex(String... hex) throws IOException {
		return from(disHex(hex));
	}

	public static AMFBodies from64(String... s) throws IOException {
		return from(dis64(s));
	}

	public static AMFBodies from(byte[] b) throws IOException {
		return from(dis(b));
	}

	private static DataInputStream disHex(String... s) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (String s1 : s)
			baos.write(HexFormat.of().parseHex(s1));
		return dis(baos.toByteArray());
	}

	private static DataInputStream dis64(String... s) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (String s1 : s)
			baos.write(Base64.getDecoder().decode(s1));
		return dis(baos.toByteArray());
	}

	private static DataInputStream dis(byte[] b) {
		return new DataInputStream(new ByteArrayInputStream(b));
	}
	
	public static String getObjectTypeDescription(byte type) {
		switch (type) {
		case AMFBody.DATA_TYPE_UNKNOWN:
			return "UNKNOWN";
		case AMFBody.DATA_TYPE_NUMBER:
			return "NUMBER";
		case AMFBody.DATA_TYPE_BOOLEAN:
			return "BOOLEAN";
		case AMFBody.DATA_TYPE_STRING:
			return "STRING";
		case AMFBody.DATA_TYPE_OBJECT:
			return "OBJECT";
		case 4:
			return "MOVIECLIP";
		case AMFBody.DATA_TYPE_NULL:
			return "NULL";
		case 6:
			return "UNDEFINED";
		case 7:
			return "REFERENCE";
		case 8:
			return "MIXED_ARRAY";
		case 9:
			return "OBJECT_END";
		case AMFBody.DATA_TYPE_ARRAY:
			return "ARRAY";
		case AMFBody.DATA_TYPE_DATE:
			return "DATE";
		case 12:
			return "LONG_STRING";
		case AMFBody.DATA_TYPE_AS_OBJECT:
			return "AS_OBJECT";
		case 14:
			return "RECORDSET";
		case AMFBody.DATA_TYPE_XML:
			return "XML";
		case AMFBody.DATA_TYPE_CUSTOM_CLASS:
			return "CUSTOM_CLASS";
		default:
			return "UNKNOWN: 0x" + Integer.toBinaryString(type);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		forEach(x -> {
			sb.append("{AMFBody: {target=");
			sb.append(x.getTarget());
			sb.append(", response=");
			sb.append(x.getResponse());
			sb.append(", type=");
			sb.append(getObjectTypeDescription(x.getType()));
			sb.append(", value=");
			if (x.getValue() instanceof ArrayList<?>) {
				sb.append('[');
				String c = ((ArrayList<?>) (x.getValue())).stream()
						.map(y -> "" + y + ":" + (y==null? null: y.getClass().getCanonicalName())).collect(Collectors.joining(","));
				sb.append(c);
				sb.append(']');
			} else if (x.getValue() instanceof String x2)
				sb.append(x2 + ":" + x2.length());
			else if (x.getValue() != null)
				sb.append(x.getValue() + ":" + x.getValue().getClass().getCanonicalName());
			else
				sb.append("null");
			sb.append("}\n");
		});
		if (sb.length() > 1)
			sb.deleteCharAt(sb.length() - 1);
		sb.append("]");
		return sb.toString();
	}
	/**public static List<?> generics(Object x3){
		return Arrays.asList(((ParameterizedType)x3.getClass().getGenericSuperclass())
			      .getActualTypeArguments());
	}*/
}




