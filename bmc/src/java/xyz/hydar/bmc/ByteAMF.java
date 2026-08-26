package xyz.hydar.bmc;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public class ByteAMF extends JsonAMFSerializer {
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