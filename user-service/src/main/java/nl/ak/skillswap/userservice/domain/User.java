package nl.ak.skillswap.userservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId; // Keycloak sub

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "allow_matching", nullable = false)
    private boolean allowMatching;

    @Column(name = "allow_emails", nullable = false)
    private boolean allowEmails;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void updateProfile(String displayName, String timeZone, String bio) {
        this.displayName = displayName;
        this.timeZone = timeZone;
        this.bio = bio;
    }

    public void updatePreferences(boolean allowMatching, boolean allowEmails) {
        this.allowMatching = allowMatching;
        this.allowEmails = allowEmails;
    }

    public void softDeleteNow() {
        this.active = false;
        this.deletedAt = OffsetDateTime.now();
    }
}
