package com.assignment.githubsearcher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "github_repositories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryEntity {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner", nullable = false)
    private String owner;

    @Column(name = "language")
    private String language;

    @Column(name = "stars", nullable = false)
    private Integer stars;

    @Column(name = "forks", nullable = false)
    private Integer forks;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
}
