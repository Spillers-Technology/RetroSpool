package io.retrospool.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Per-tenant export target; multiple allowed, every capture fans out to each enabled one (D-004). */
@Entity
@Table(name = "export_destination")
public class ExportDestination {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DestinationType type;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * S3: bucket/prefix/region/endpoint/pathStyleAccess.
     * SFTP: host/port/username/remote_path/host_key_fingerprint.
     * FTPS: host/port/username/remote_path/implicit_tls.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private String config = "{}";

    /** SFTP password ref (password auth per D-010). Write-only (D-012). */
    @Column(name = "secret_ref")
    private String secretRef;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected ExportDestination() {
    }

    public ExportDestination(UUID tenantId, DestinationType type, String name, String config) {
        this.tenantId = tenantId;
        this.type = type;
        this.name = name;
        this.config = config;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public DestinationType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
