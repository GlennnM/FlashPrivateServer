
<%@page import="java.util.Arrays"%>
<%@page import="java.util.function.Function"%>
<%@page import="java.util.HashMap"%>
<%@page import="java.util.Date"%>
<%@page import="java.io.Serializable"%>
<%@page import="org.json.JSONArray"%>
<%@page import="java.util.Map"%>
<%@page import="org.json.JSONObject"%>
<%@page import="java.util.stream.Collectors"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Base64"%>
<%@page import="java.io.DataOutputStream"%>
<%@page import="java.io.OutputStream"%>
<%@page import="java.util.Iterator"%>
<%@page import="java.io.ByteArrayInputStream"%>
<%@page import="java.io.ByteArrayOutputStream"%>
<%@page import="java.nio.file.Files"%>
<%@page import="java.nio.file.Paths"%>
<%@page import="java.io.DataInputStream"%>
<%@page import="java.io.IOException"%>
<%@page import="java.io.InputStream"%>
<%@page import="java.io.PrintStream"%>
<%@page
	import="java.util.HexFormat,java.util.List,org.openamf.io.*,org.openamf.*"%>
<%-- From https://github.com/GlennnM/AMFGateway repo. i need to organize this stuff or something --%>
<%!/**statics*/
	public static class AMFBodies implements Iterable<AMFBody> {
		AMFMessage msg;

		private AMFBodies(AMFMessage msg) {
			this.msg = msg;
		}

		public static AMFBodies from(AMFMessage msg) {
			return new AMFBodies(msg);
		}

		public static AMFBodies from(InputStream is) throws IOException {
			PrintStream old = System.out;
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

		@Override
		public Iterator<AMFBody> iterator() {
			return new Iterator<AMFBody>() {
				private int index = 0;

				@Override
				public boolean hasNext() {
					return index < msg.getBodyCount();
				}

				@Override
				public AMFBody next() {
					return msg.getBodyAt(index++);
				}
			};
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
							.map(y -> "" + y + ":" + y.getClass().getCanonicalName()).collect(Collectors.joining(","));
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

	public static class JsonAMFSerializer extends AMFSerializer {
		JsonAMFSerializer(DataOutputStream dis) {
			super(dis);
		}

		public void writeMapAsObj(Map<?, ?> j) throws IOException {
			this.outputStream.writeByte(3);
			for (var entry : j.entrySet()) {
				/**TODO: allow byte[] of ach data*/
				this.outputStream.writeUTF((String) entry.getKey());
				this.writeData(entry.getValue());
				//System.out.println(entry.getValue().getClass().getCanonicalName());
			}

			this.outputStream.writeShort(0);
			this.outputStream.writeByte(9);
		}

		public void writeJSONObject(JSONObject j) throws IOException {
			writeMapAsObj(j.toMap());
		}

		public void writeJSONArray(JSONArray j) throws IOException {
			List<Object> list = j.toList();
			this.outputStream.writeByte(10);
			this.outputStream.writeInt(list.size());
			for (Object object : list) {
				System.out.println(object.getClass().getCanonicalName());
				writeData(object);
			}
		}

		public void writeBoolean(Boolean b) throws IOException {
			this.outputStream.writeByte(1);
			this.outputStream.writeBoolean(b);
		}
		@Override
		public void writeData(Object obj) throws IOException {
			if (obj instanceof Map<?, ?> j)
				writeMapAsObj(j);
			else if (obj instanceof JSONArray j)
				writeJSONArray(j);
			else if (obj instanceof JSONObject k)
				writeJSONObject(k);
			else if (obj instanceof Boolean b)
				writeBoolean(b);
			else
				super.writeData(obj);
		}
	}

	private static class ByteAMF extends JsonAMFSerializer {
		private ByteArrayOutputStream baos;

		public static ByteAMF serializer() {
			var baos = new ByteArrayOutputStream();
			return new ByteAMF(baos).setBAOS(baos);
		}

		private ByteAMF setBAOS(ByteArrayOutputStream baos) {
			this.baos = baos;
			return this;
		}

		public ByteAMF(ByteArrayOutputStream baos) {
			super(new DataOutputStream(baos));
		}

		public byte[] get() {
			return baos.toByteArray();
		}

	}

	enum AMFType {
		UNKNOWN(AMFBody.DATA_TYPE_UNKNOWN), NUMBER(AMFBody.DATA_TYPE_NUMBER), BOOLEAN(AMFBody.DATA_TYPE_BOOLEAN),
		STRING(AMFBody.DATA_TYPE_STRING), OBJECT(AMFBody.DATA_TYPE_OBJECT), NULL(AMFBody.DATA_TYPE_NULL),
		ARRAY(AMFBody.DATA_TYPE_ARRAY), DATE(AMFBody.DATA_TYPE_DATE);

		byte code;

		public static AMFType fromPattern(Object o) {
			if (o instanceof String s)
				for (AMFType t : values()) {
					if (t.toString().equals(s))
						return t;
				}
			return infer(o);
		}

		public static AMFType infer(Object o) {
			if (o == null)
				return NULL;
			else if (o instanceof AMFType t)
				return t;
			else if (o instanceof Number)
				return NUMBER;
			else if (o instanceof Boolean)
				return BOOLEAN;
			else if (o instanceof String)
				return STRING;
			else if ((o instanceof List<?>) || (o instanceof JSONArray))
				return ARRAY;
			else if ((o instanceof Date))
				return DATE;
			else if ((o instanceof Map) || (o instanceof JSONObject) || (Class<?>) o.getClass() == Object.class
					|| (o instanceof Serializable))
				return OBJECT;
			return UNKNOWN;
		}

		public boolean allows(AMFType t) {
			return (this == t) || (t == null) || (t == NULL);
		}

		public static byte inferCode(Object o) {
			return infer(o).code;
		}

		AMFType(byte b) {
			code = b;
		}
	}

	private static class AMFService {

		public String name;
		private List<AMFType> inputTypes;
		private static Map<String, AMFService> services = new HashMap<>();
		private Function<List<?>, ?> svc = null;

		public AMFService inputs(List<?> list) {
			inputTypes = list.stream().map(AMFType::fromPattern).toList();
			return this;
		}

		public AMFService inputs(Object... list) {
			return inputs(Arrays.asList(list));
		}

		public AMFService(String name, Function<List<?>, ?> svc) {
			this(name, svc, null);
		}

		public AMFService(String name) {
			this(name, null, null);
		}

		private AMFService(String name, Function<List<?>, ?> svc, List<AMFType> inputTypes) {
			this.name = name;
			this.svc = svc;
			this.inputTypes = inputTypes;
		}

		private AMFService register() {
			services.put(this.name, this);
			return this;
		}

		protected Object apply(List<?> input) throws Exception {
			if (svc == null)
				return null;
			return svc.apply(input);
		}

		public static void accept(InputStream input, OutputStream output) throws IOException {
			AMFMessage out = new AMFMessage();
			var serializer = new JsonAMFSerializer(new DataOutputStream(output));
			var h = AMFBodies.from(input);
			System.out.println(h);
			for (var body : h) {
				out.addBody(accept(body));
			}
			serializer.serialize(out);
		}

		public static AMFBody accept(AMFBody input) {
			String name = input.getTarget();
			AMFService svc = getService(name);
			String response = input.getResponse();
			List<?> list;
			if (!(input.getValue() instanceof List<?>)) {
				list = List.of(input.getValue());
			} else
				list = (List<?>) input.getValue();
			Object ret = null;
			if (svc != null && svc.validateList(list)) {
				try {
					return new AMFBody(response + "/onResult", "null", svc.apply(list), AMFType.inferCode(ret));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			//System.out.println(":(");
			//System.out.println(input.getValue());
			return new AMFBody(response + "/onResult", "null", null, AMFType.NULL.code);
		}
		
		public static AMFService getService(String target){
			return services.get(target);
		}
		
		protected boolean validate(int index, Object item) {
			return true;
		}

		protected boolean validateType(int index, Object item) {
			//System.out.println(""+inputTypes.get(index)+":"+AMFType.infer(item));
			return inputTypes.get(index).allows(AMFType.infer(item));
		}

		private boolean validateList(List<?> items) {
			int i = 0;
			if (inputTypes == null)
				return true;
			if (inputTypes.size() != items.size()) {
				//System.out.println(""+inputTypes+""+items.stream().map(AMFType::infer).collect(Collectors.toList()));
				return false;
			}
			for (Object o : items) {
				if (!validate(i, o) || !validateType(i, o)) {
					return false;
				}
				i++;
			}
			return true;
		}
	}
	%>