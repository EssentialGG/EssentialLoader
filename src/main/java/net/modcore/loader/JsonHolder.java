package net.modcore.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
class JsonHolder {
    private JsonObject object;

    JsonHolder(JsonObject object) {
        this.object = object;
    }

    JsonHolder(String raw) {
        if (raw == null)
            object = new JsonObject();
        else
            try {
                this.object = new JsonParser().parse(raw).getAsJsonObject();
            } catch (Exception e) {
                this.object = new JsonObject();
                e.printStackTrace();
            }
    }

    JsonHolder() {
        this(new JsonObject());
    }

    @Override
    public String toString() {
        if (object != null)
            return object.toString();
        return "{}";
    }

    public JsonHolder put(String key, boolean value) {
        object.addProperty(key, value);
        return this;
    }

    public void mergeNotOverride(JsonHolder merge) {
        merge(merge, false);
    }

    public void mergeOverride(JsonHolder merge) {
        merge(merge, true);
    }

    public void merge(JsonHolder merge, boolean override) {
        JsonObject object = merge.getObject();
        for (String s : merge.getKeys()) {
            if (override || !this.has(s))
                put(s, object.get(s));
        }
    }

    private void put(String s, JsonElement element) {
        this.object.add(s, element);
    }

    public JsonHolder put(String key, String value) {
        object.addProperty(key, value);
        return this;
    }

    public JsonHolder put(String key, int value) {
        object.addProperty(key, value);
        return this;
    }

    public JsonHolder put(String key, double value) {
        object.addProperty(key, value);
        return this;
    }

    public JsonHolder put(String key, long value) {
        object.addProperty(key, value);
        return this;
    }

    private JsonHolder defaultOptJSONObject(String key, JsonObject fallBack) {
        try {
            return new JsonHolder(object.get(key).getAsJsonObject());
        } catch (Exception e) {
            return new JsonHolder(fallBack);
        }
    }

    public JsonArray defaultOptJSONArray(String key, JsonArray fallback) {
        try {
            return object.get(key).getAsJsonArray();
        } catch (Exception e) {
            return fallback;
        }
    }

    public JsonArray optJSONArray(String key) {
        return defaultOptJSONArray(key, new JsonArray());
    }


    public boolean has(String key) {
        return object.has(key);
    }

    public long optLong(String key, long fallback) {
        try {
            return object.get(key).getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    public long optLong(String key) {
        return optLong(key, 0);
    }

    public boolean optBoolean(String key, boolean fallback) {
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    public boolean optBoolean(String key) {
        return optBoolean(key, false);
    }

    public JsonObject optActualJSONObject(String key) {
        try {
            return object.get(key).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public JsonHolder optJSONObject(String key) {
        return defaultOptJSONObject(key, new JsonObject());
    }


    public int optInt(String key, int fallBack) {
        try {
            return object.get(key).getAsInt();
        } catch (Exception e) {
            return fallBack;
        }
    }

    public int optInt(String key) {
        return optInt(key, 0);
    }


    public String defaultOptString(String key, String fallBack) {
        try {
            return object.get(key).getAsString();
        } catch (Exception e) {
            return fallBack;
        }
    }

    public String optString(String key) {
        return defaultOptString(key, "");
    }


    public double optDouble(String key, double fallBack) {
        try {
            return object.get(key).getAsDouble();
        } catch (Exception e) {
            return fallBack;
        }
    }

    public List<String> getKeys() {
        List<String> tmp = new ArrayList<>();
        object.entrySet().forEach(e -> tmp.add(e.getKey()));
        return tmp;
    }

    public double optDouble(String key) {
        return optDouble(key, 0.0);
    }


    public JsonObject getObject() {
        return object;
    }

    public boolean isNull(String key) {
        return object.has(key) && object.get(key).isJsonNull();
    }

    public JsonHolder put(String values, JsonHolder values1) {
        return put(values, values1.getObject());
    }

    public JsonHolder put(String values, JsonObject object) {
        this.object.add(values, object);
        return this;
    }

    public void put(String blacklisted, JsonArray jsonElements) {
        this.object.add(blacklisted, jsonElements);
    }

    public void remove(String header) {
        object.remove(header);
    }
}