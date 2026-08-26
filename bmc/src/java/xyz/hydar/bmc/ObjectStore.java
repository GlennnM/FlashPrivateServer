package xyz.hydar.bmc;

import java.util.List;
import java.util.function.UnaryOperator;

import org.json.JSONObject;

/** stuff like put(url, ..., ...) */
public interface ObjectStore {
	public default JSONObject get(String... url) {
		return get(String.join("/", url));
	}

	public default JSONObject get(Iterable<String> url) {
		return get(String.join("/", url));
	}

	public default boolean has(String... url) {
		return has(String.join("/", url));
	}

	public default boolean has(Iterable<String> url) {
		return has(String.join("/", url));
	}

	public default JSONObject get(String url, JSONObject fallback) {
		var ret = get(url);
		return ret == null ? fallback : ret;
	}

	public default JSONObject get(Iterable<String> url, JSONObject fallback) {
		var ret = get(url);
		return ret == null ? fallback : ret;
	}

	public default boolean put(Iterable<String> url, JSONObject payload) {
		return put(String.join("/", url), payload);
	}

	public default JSONObject update(Iterable<String> url, UnaryOperator<JSONObject> update) {
		return update(String.join("/", url), update);
	}

	public default boolean delete(String... url) {
		return delete(String.join("/", url));
	}

	public default boolean delete(Iterable<String> url) {
		return delete(String.join("/", url));
	}

	public List<String> list();

	public JSONObject get(String url);

	public boolean has(String url);

	public boolean delete(String url);

	public boolean put(String url, JSONObject payload);

	public default JSONObject update(String url, UnaryOperator<JSONObject> update) {
		var input = get(url);
		put(url, update.apply(get(url)));
		return input;
	}
}