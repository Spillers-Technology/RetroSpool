package io.retrospool.storage;

/** Landing zone for captured artifacts (S3 API; MinIO locally). */
public interface ObjectStore {

    void put(String key, byte[] bytes, String contentType);

    byte[] get(String key);

    boolean exists(String key);
}
