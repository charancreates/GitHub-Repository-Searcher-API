package com.assignment.githubsearcher.repository;

import com.assignment.githubsearcher.entity.RepositoryEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GitHubRepositoryRepo extends JpaRepository<RepositoryEntity, Long> {

    @Query(value = "SELECT * FROM github_repositories r " +
                   "WHERE (:language IS NULL OR LOWER(CAST(r.language AS text)) = LOWER(CAST(:language AS text))) " +
                   "AND (:minStars IS NULL OR r.stars >= :minStars)",
           nativeQuery = true)
    List<RepositoryEntity> findByFilters(
            @Param("language") String language,
            @Param("minStars") Integer minStars,
            Sort sort
    );
}
