package xyz.hydar.bmc;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openamf.io.AMFSerializer;

public class JsonAMFSerializer extends AMFSerializer {
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
			writeData(object);
		}
	}

	public void writeBoolean(Boolean b) throws IOException {
		this.outputStream.writeByte(1);
		this.outputStream.writeBoolean(b);
	}
	@Override
	public void writeData(Object obj) throws IOException {
		if (obj == null)
			super.writeData(obj);
		else if (obj == JSONObject.NULL)
			super.writeData(null);
		else if (obj instanceof Map<?, ?> j)
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

