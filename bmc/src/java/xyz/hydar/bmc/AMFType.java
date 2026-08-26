package xyz.hydar.bmc;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openamf.AMFBody;

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
		if (o == null || o == JSONObject.NULL)
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