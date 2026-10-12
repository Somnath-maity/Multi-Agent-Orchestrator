package com.multiagent.orchestrator.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "steps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Step {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepType type;

    /**
     * Comma-separated UUIDs of steps this step depends on.
     * Use {@link #getDependsOnList()} and {@link #setDependsOnList(List)} for typed access.
     */
    @Column(name = "depends_on", columnDefinition = "TEXT")
    private String dependsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(nullable = false)
    @Builder.Default
    private int attempt = 0;

    @Column(name = "eval_score")
    private Double evalScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Returns the depends_on field parsed as a list of UUIDs.
     */
    public List<UUID> getDependsOnList() {
        if (dependsOn == null || dependsOn.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(dependsOn.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    /**
     * Sets the depends_on field from a list of UUIDs.
     */
    public void setDependsOnList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            this.dependsOn = "";
        } else {
            this.dependsOn = ids.stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));
        }
    }
}
