package io.retrospool.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * One company (one HOD .ws file == one tenant). Promoted from an approved
 * submission — never created directly by the landing surface (D-007).
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "host", nullable = false)
    private String host;

    /** Informational for spool ops; signon uses JTOpen's own port selection. */
    @Column(name = "port", nullable = false)
    private int port = 9476;

    @Column(name = "use_ssl", nullable = false)
    private boolean useSsl = true;

    @Column(name = "username", nullable = false)
    private String username;

    /** IBM i password indirection (env var name / Vault path). Write-only (D-012). */
    @Column(name = "secret_ref")
    private String secretRef;

    /** HOD LU/device name; informational, not used at runtime. */
    @Column(name = "printer_device_name")
    private String printerDeviceName;

    @Column(name = "ccsid")
    private Integer ccsid;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "library_list", nullable = false, columnDefinition = "text[]")
    private List<String> libraryList = new ArrayList<>();

    /** Tenant default; per-queue nullable override wins (D-009). */
    @Enumerated(EnumType.STRING)
    @Column(name = "retention_policy", nullable = false)
    private RetentionPolicy retentionPolicy = RetentionPolicy.HOLD;

    @Column(name = "poll_interval_seconds", nullable = false)
    private int pollIntervalSeconds = 60;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
    }

    public Tenant(String name, String host, String username) {
        this.name = name;
        this.host = host;
        this.username = username;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public String getPrinterDeviceName() {
        return printerDeviceName;
    }

    public void setPrinterDeviceName(String printerDeviceName) {
        this.printerDeviceName = printerDeviceName;
    }

    public Integer getCcsid() {
        return ccsid;
    }

    public void setCcsid(Integer ccsid) {
        this.ccsid = ccsid;
    }

    public List<String> getLibraryList() {
        return libraryList;
    }

    public void setLibraryList(List<String> libraryList) {
        this.libraryList = libraryList;
    }

    public RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(RetentionPolicy retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public void setPollIntervalSeconds(int pollIntervalSeconds) {
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
