package com.assignment.githubsearcher.service;

import com.assignment.githubsearcher.dto.GitHubApiResponse;
import com.assignment.githubsearcher.dto.RepositoriesResponseDto;
import com.assignment.githubsearcher.dto.RepositoryDto;
import com.assignment.githubsearcher.dto.SearchRequestDto;
import com.assignment.githubsearcher.dto.SearchResponseDto;
import com.assignment.githubsearcher.entity.RepositoryEntity;
import com.assignment.githubsearcher.exception.GitHubApiException;
import com.assignment.githubsearcher.repository.GitHubRepositoryRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitHubService {

    private final RestClient githubRestClient;
    private final GitHubRepositoryRepo repositoryRepo;

    @Transactional
    public SearchResponseDto searchAndSaveRepositories(SearchRequestDto request) {
        StringBuilder queryBuilder = new StringBuilder(request.getQuery().trim());
        if (StringUtils.hasText(request.getLanguage())) {
            queryBuilder.append(" language:").append(request.getLanguage().trim());
        }

        String sortParam = StringUtils.hasText(request.getSort()) ? request.getSort().trim().toLowerCase() : "stars";

        GitHubApiResponse apiResponse = fetchFromGitHub(queryBuilder.toString(), sortParam);

        if (apiResponse == null || apiResponse.getItems() == null || apiResponse.getItems().isEmpty()) {
            return SearchResponseDto.builder()
                    .message("No repositories found matching the search criteria")
                    .repositories(Collections.emptyList())
                    .build();
        }

        List<RepositoryEntity> entities = apiResponse.getItems().stream()
                .map(this::mapToEntity)
                .toList();

        // Using GitHub's unique ID as @Id ensures saveAll performs an upsert (update if exists, insert if new)
        List<RepositoryEntity> savedEntities = repositoryRepo.saveAll(entities);

        List<RepositoryDto> dtos = savedEntities.stream()
                .map(this::mapToDto)
                .toList();

        return SearchResponseDto.builder()
                .message("Repositories fetched and saved successfully")
                .repositories(dtos)
                .build();
    }

    public RepositoriesResponseDto getStoredRepositories(String language, Integer minStars, String sort) {
        if (minStars != null && minStars < 0) {
            throw new IllegalArgumentException("minStars cannot be negative");
        }

        Sort sortOrder = resolveSort(sort);
        String cleanLanguage = StringUtils.hasText(language) ? language.trim() : null;

        List<RepositoryEntity> entities = repositoryRepo.findByFilters(cleanLanguage, minStars, sortOrder);
        List<RepositoryDto> dtos = entities.stream()
                .map(this::mapToDto)
                .toList();

        return RepositoriesResponseDto.builder()
                .repositories(dtos)
                .build();
    }

    private GitHubApiResponse fetchFromGitHub(String query, String sort) {
        try {
            return githubRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/repositories")
                            .queryParam("q", query)
                            .queryParam("sort", sort)
                            .queryParam("order", "desc")
                            .build())
                    .retrieve()
                    .body(GitHubApiResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN || e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new GitHubApiException("GitHub API rate limit exceeded. Please try again later or configure a GITHUB_TOKEN.", HttpStatus.TOO_MANY_REQUESTS);
            }
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new GitHubApiException("Invalid search query sent to GitHub API.", HttpStatus.BAD_REQUEST);
            }
            throw new GitHubApiException("GitHub API returned an error: " + e.getStatusCode(), HttpStatus.BAD_GATEWAY);
        } catch (RestClientException e) {
            throw new GitHubApiException("Failed to communicate with GitHub API: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private Sort resolveSort(String sort) {
        if (!StringUtils.hasText(sort) || "stars".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "stars");
        }
        if ("forks".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "forks");
        }
        if ("updated".equalsIgnoreCase(sort)) {
            // findByFilters is a native SQL query, so dynamic sorting must use
            // the PostgreSQL column name rather than the entity property name.
            return Sort.by(Sort.Direction.DESC, "last_updated");
        }
        throw new IllegalArgumentException("Invalid sort parameter. Allowed values: stars, forks, updated");
    }

    private RepositoryEntity mapToEntity(GitHubApiResponse.GitHubRepoItem item) {
        return RepositoryEntity.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .owner(item.getOwner() != null ? item.getOwner().getLogin() : "unknown")
                .language(item.getLanguage())
                .stars(item.getStargazersCount() != null ? item.getStargazersCount() : 0)
                .forks(item.getForksCount() != null ? item.getForksCount() : 0)
                .lastUpdated(item.getUpdatedAt() != null ? item.getUpdatedAt() : Instant.now())
                .build();
    }

    private RepositoryDto mapToDto(RepositoryEntity entity) {
        return RepositoryDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .owner(entity.getOwner())
                .language(entity.getLanguage())
                .stars(entity.getStars())
                .forks(entity.getForks())
                .lastUpdated(entity.getLastUpdated())
                .build();
    }
}
