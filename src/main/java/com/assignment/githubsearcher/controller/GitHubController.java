package com.assignment.githubsearcher.controller;

import com.assignment.githubsearcher.dto.RepositoriesResponseDto;
import com.assignment.githubsearcher.dto.SearchRequestDto;
import com.assignment.githubsearcher.dto.SearchResponseDto;
import com.assignment.githubsearcher.service.GitHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
@Tag(name = "GitHub Repositories", description = "Endpoints to search GitHub repositories and retrieve stored results")
public class GitHubController {

    private final GitHubService gitHubService;

    @PostMapping("/search")
    @Operation(summary = "Search GitHub repositories and save to PostgreSQL",
               description = "Fetches matching repositories from GitHub REST API and upserts them into PostgreSQL.")
    public ResponseEntity<SearchResponseDto> searchRepositories(@Valid @RequestBody SearchRequestDto request) {
        SearchResponseDto response = gitHubService.searchAndSaveRepositories(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/repositories")
    @Operation(summary = "Retrieve stored repositories",
               description = "Returns stored repositories from PostgreSQL with optional filtering by language, minimum stars, and sorting.")
    public ResponseEntity<RepositoriesResponseDto> getRepositories(
            @Parameter(description = "Filter by programming language (e.g., Java)")
            @RequestParam(required = false) String language,

            @Parameter(description = "Filter by minimum star count (e.g., 100)")
            @RequestParam(required = false) Integer minStars,

            @Parameter(description = "Sort by stars, forks, or updated (default: stars)")
            @RequestParam(required = false, defaultValue = "stars") String sort
    ) {
        RepositoriesResponseDto response = gitHubService.getStoredRepositories(language, minStars, sort);
        return ResponseEntity.ok(response);
    }
}
