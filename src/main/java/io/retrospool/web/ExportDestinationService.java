package io.retrospool.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.DestinationType;
import io.retrospool.persistence.ExportAttemptRepository;
import io.retrospool.persistence.ExportDestination;
import io.retrospool.persistence.ExportDestinationRepository;
import io.retrospool.persistence.TenantRepository;
import io.retrospool.secrets.SecretStorageUnavailableException;
import io.retrospool.secrets.SecretWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Admin CRUD for {@link ExportDestination} (Phase 6/7). Tenant isolation follows the
 * same shape as {@link CaptureAdminController#download}: every lookup by id goes through
 * {@link ExportDestinationRepository#findByTenantIdAndId} so a destination id that exists
 * but belongs to another tenant surfaces as {@link NoSuchElementException} (mapped to 404
 * by {@link AdminExceptionAdvice}), never a 403 that would confirm the id exists elsewhere
 * (D-008). Passwords are write-only end to end (D-012/D-023): a plaintext password is
 * accepted only long enough to hand it to {@link SecretWriter#write}, which returns the
 * opaque {@code secret_ref} actually persisted on the entity — the plaintext is scrubbed
 * from the local buffer immediately after and never logged, stored, or returned.
 */
@Service
public class ExportDestinationService {

    private static final Map<DestinationType, Set<String>> REQUIRED_FIELDS = Map.of(
            DestinationType.S3, Set.of("bucket", "region"),
            DestinationType.SFTP, Set.of("host", "username", "remote_path"),
            DestinationType.FTPS, Set.of("host", "username", "remote_path"));

    private static final Map<DestinationType, Set<String>> ALLOWED_FIELDS = Map.of(
            DestinationType.S3, Set.of("bucket", "region", "prefix", "endpoint", "pathStyleAccess"),
            DestinationType.SFTP, Set.of("host", "port", "username", "remote_path", "host_key_fingerprint"),
            DestinationType.FTPS, Set.of("host", "port", "username", "remote_path", "implicit_tls"));

    private static final Map<DestinationType, Set<String>> TEXT_FIELDS = Map.of(
            DestinationType.S3, Set.of("bucket", "region", "prefix", "endpoint"),
            DestinationType.SFTP, Set.of("host", "username", "remote_path", "host_key_fingerprint"),
            DestinationType.FTPS, Set.of("host", "username", "remote_path"));

    private static final Map<DestinationType, Set<String>> BOOLEAN_FIELDS = Map.of(
            DestinationType.S3, Set.of("pathStyleAccess"),
            DestinationType.SFTP, Set.of(),
            DestinationType.FTPS, Set.of("implicit_tls"));

    private final ExportDestinationRepository destinations;
    private final ExportAttemptRepository attempts;
    private final TenantRepository tenants;
    private final AuditEventRepository audit;
    private final SecretWriter secretWriter;
    private final ObjectMapper mapper;

    public ExportDestinationService(
            ExportDestinationRepository destinations,
            ExportAttemptRepository attempts,
            TenantRepository tenants,
            AuditEventRepository audit,
            SecretWriter secretWriter,
            ObjectMapper mapper) {
        this.destinations = destinations;
        this.attempts = attempts;
        this.tenants = tenants;
        this.audit = audit;
        this.secretWriter = secretWriter;
        this.mapper = mapper;
    }

    @Transactional
    public ExportDestination create(UUID tenantId, AdminDtos.CreateDestinationRequest request, String actor) {
        if (tenants.findById(tenantId).isEmpty()) {
            throw new NoSuchElementException("tenant not found: " + tenantId);
        }
        DestinationType type = parseType(request.type());
        JsonNode config = validateConfig(type, request.config());
        String name = requireName(request.name());
        rejectPasswordForS3(type, request.password());

        ExportDestination destination = new ExportDestination(tenantId, type, name, config.toString());
        if (StringUtils.hasText(request.password())) {
            destination.setSecretRef(storeSecret(request.password()));
        }
        destinations.save(destination);

        audit.save(new AuditEvent(tenantId, "destination.created",
                jsonPayload("id", destination.getId().toString(), "type", type.name(), "name", name, "actor", actor)));
        return destination;
    }

    @Transactional
    public ExportDestination update(UUID tenantId, UUID id, AdminDtos.UpdateDestinationRequest request, String actor) {
        ExportDestination destination = require(tenantId, id);

        if (request.name() != null) {
            destination.setName(requireName(request.name()));
        }
        if (request.config() != null) {
            destination.setConfig(validateConfig(destination.getType(), request.config()).toString());
        }
        if (request.enabled() != null) {
            destination.setEnabled(request.enabled());
        }
        if (StringUtils.hasText(request.password())) {
            rejectPasswordForS3(destination.getType(), request.password());
            // Rotation: overwrite secret_ref with a freshly written secret. No deletion
            // convention exists for the old row (DatabaseSecretStore never deletes; see
            // its javadoc) — this matches that precedent rather than inventing one.
            destination.setSecretRef(storeSecret(request.password()));
        }
        destinations.save(destination);

        audit.save(new AuditEvent(tenantId, "destination.updated", jsonPayload("id", id.toString(), "actor", actor)));
        return destination;
    }

    @Transactional
    public ExportDestination setEnabled(UUID tenantId, UUID id, boolean enabled, String actor) {
        ExportDestination destination = require(tenantId, id);
        destination.setEnabled(enabled);
        destinations.save(destination);

        audit.save(new AuditEvent(tenantId, enabled ? "destination.enabled" : "destination.disabled",
                jsonPayload("id", id.toString(), "actor", actor)));
        return destination;
    }

    /**
     * Hard-delete only when the destination has never attempted a delivery (D-004). A
     * destination with {@link io.retrospool.persistence.ExportAttempt} history is blocked
     * (409) rather than hard-deleted, even though the schema's {@code ON DELETE CASCADE}
     * would allow it cleanly — cascading would silently erase delivery history an MSP
     * customer may need for a support or billing dispute. The admin disables it instead
     * (which is reversible and keeps the history queryable); once we build attempt-history
     * pruning/archival this constraint can be revisited.
     */
    @Transactional
    public void delete(UUID tenantId, UUID id, String actor) {
        ExportDestination destination = require(tenantId, id);
        if (attempts.existsByExportDestinationId(id)) {
            throw new IllegalStateException(
                    "cannot delete a destination with export attempt history — disable it instead");
        }
        try {
            destinations.delete(destination);
            // V4 changes the database FK to ON DELETE NO ACTION. Flushing here turns a
            // concurrent attempt insert into the same deliberate 409 instead of a late
            // transaction-commit failure (and, critically, never erases attempt history).
            destinations.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "cannot delete a destination with export attempt history — disable it instead", e);
        }

        audit.save(new AuditEvent(tenantId, "destination.deleted",
                jsonPayload("id", id.toString(), "name", destination.getName(), "actor", actor)));
    }

    private ExportDestination require(UUID tenantId, UUID id) {
        return destinations.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NoSuchElementException("export destination not found: " + id));
    }

    private String storeSecret(String password) {
        if (!secretWriter.isEnabled()) {
            throw new SecretStorageUnavailableException(
                    "This server cannot store destination passwords yet — the operator must set "
                            + "SECRET_ENCRYPTION_KEY. Retry without a password, or ask the operator to "
                            + "enable secret storage.");
        }
        char[] chars = password.toCharArray();
        try {
            return secretWriter.write(chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private static DestinationType parseType(String type) {
        try {
            return DestinationType.valueOf(type.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unknown destination type: " + type);
        }
    }

    private static String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.trim();
    }

    private static void rejectPasswordForS3(DestinationType type, String password) {
        if (type == DestinationType.S3 && StringUtils.hasText(password)) {
            throw new IllegalArgumentException("password is only valid for SFTP or FTPS destinations");
        }
    }

    /**
     * Enforces that {@code config}'s fields exactly match the shape declared for
     * {@code type} (D-004/D-005) — rejects both a missing required field and a field
     * that belongs to a different destination type (e.g. an S3 {@code bucket} on an
     * SFTP destination), so a shape mismatch is caught here rather than at export time.
     */
    private JsonNode validateConfig(DestinationType type, JsonNode config) {
        if (config == null || !config.isObject()) {
            throw new IllegalArgumentException("config must be a JSON object matching type " + type);
        }
        Set<String> required = REQUIRED_FIELDS.get(type);
        Set<String> allowed = ALLOWED_FIELDS.get(type);

        List<String> missing = required.stream().filter(field -> !config.hasNonNull(field)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "config for " + type + " is missing required field(s): " + missing);
        }

        List<String> unexpected = new ArrayList<>();
        config.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                unexpected.add(field);
            }
        });
        if (!unexpected.isEmpty()) {
            throw new IllegalArgumentException(
                    "config has field(s) not valid for type " + type + ": " + unexpected);
        }

        // Persist a normalized object rather than whatever coercions JsonNode.asText()
        // might permit. This keeps the JSONB contract usable by the future exporters:
        // strings stay strings, ports are bounded integers, and flags are booleans.
        ObjectNode normalized = mapper.createObjectNode();
        config.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                if (required.contains(field)) {
                    throw new IllegalArgumentException("config field " + field + " must not be null");
                }
                return;
            }
            if (TEXT_FIELDS.get(type).contains(field)) {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("config field " + field + " must be a string");
                }
                String text = value.textValue().trim();
                if (!StringUtils.hasText(text)) {
                    if (required.contains(field)) {
                        throw new IllegalArgumentException("config field " + field + " must not be blank");
                    }
                    return;
                }
                normalized.put(field, text);
            } else if (field.equals("port")) {
                if (!value.isIntegralNumber() || !value.canConvertToInt()) {
                    throw new IllegalArgumentException("config field port must be an integer from 1 to 65535");
                }
                int port = value.intValue();
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("config field port must be an integer from 1 to 65535");
                }
                normalized.put(field, port);
            } else if (BOOLEAN_FIELDS.get(type).contains(field)) {
                if (!value.isBoolean()) {
                    throw new IllegalArgumentException("config field " + field + " must be a boolean");
                }
                normalized.put(field, value.booleanValue());
            }
        });
        return normalized;
    }

    private String jsonPayload(String... kv) {
        ObjectNode node = mapper.createObjectNode();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            node.put(kv[i], kv[i + 1]);
        }
        return node.toString();
    }
}
