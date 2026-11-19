package com.assignment.githubsearcher.service;

import com.assignment.githubsearcher.dto.GitHubApiResponse;
import com.assignment.githubsearcher.dto.RepositoriesResponseDto;
import com.assignment.githubsearcher.dto.SearchRequestDto;
import com.assignment.githubsearcher.dto.SearchResponseDto;
import com.assignment.githubsearcher.entity.RepositoryEntity;
import com.assignment.githubsearcher.exception.GitHubApiException;
import com.assignment.githubsearcher.repository.GitHubRepositoryRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private RestClient githubRestClient;

    @Mock
    private GitHubRepositoryRepo repositoryRepo;

    @InjectMocks
    private GitHubService gitHubService;

    @Test
    void getStoredRepositories_ShouldReturnFilteredList() {
        RepositoryEntity entity = RepositoryEntity.builder()
                .id(123456L)
                .name("spring-boot-example")
                .description("Example repo")
                .owner("user123")
                .language("Java")
                .stars(450)
                .forks(120)
                .lastUpdated(Instant.parse("2024-01-01T12:00:00Z"))
                .build();

        when(repositoryRepo.findByFilters(eq("Java"), eq(100), any(Sort.class)))
                .thenReturn(List.of(entity));

        RepositoriesResponseDto response = gitHubService.getStoredRepositories("Java", 100, "stars");

        assertEquals(1, response.getRepositories().size());
        assertEquals("spring-boot-example", response.getRepositories().get(0).getName());
        verify(repositoryRepo).findByFilters(eq("Java"), eq(100), eq(Sort.by(Sort.Direction.DESC, "stars")));
    }

    @Test
    void getStoredRepositories_WithInvalidSort_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                gitHubService.getStoredRepositories("Java", 10, "invalid_sort"));
    }

    @Test
    void getStoredRepositories_WithNegativeMinStars_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                gitHubService.getStoredRepositories("Java", -5, "stars"));
    }

    @Test
    void getStoredRepositories_WithUpdatedSort_UsesNativeColumnName() {
        when(repositoryRepo.findByFilters(eq(null), eq(null), any(Sort.class))).thenReturn(List.of());

        gitHubService.getStoredRepositories(null, null, "updated");

        verify(repositoryRepo).findByFilters(null, null, Sort.by(Sort.Direction.DESC, "last_updated"));
    }

    @Test
    void getStoredRepositories_WithForksSort_UsesForksColumn() {
        when(repositoryRepo.findByFilters(eq(null), eq(null), any(Sort.class))).thenReturn(List.of());

        gitHubService.getStoredRepositories(null, null, "forks");

        verify(repositoryRepo).findByFilters(null, null, Sort.by(Sort.Direction.DESC, "forks"));
    }

    @Test
    void searchAndSaveRepositories_WhenGitHubReturnsNoItems_ReturnsDescriptiveEmptyResponse() {
        GitHubApiResponse apiResponse = new GitHubApiResponse();
        apiResponse.setItems(List.of());
        mockGitHubResponse(apiResponse);

        SearchResponseDto response = gitHubService.searchAndSaveRepositories(
                SearchRequestDto.builder().query("spring boot").language("Java").sort("stars").build());

        assertEquals("No repositories found matching the search criteria", response.getMessage());
        assertEquals(List.of(), response.getRepositories());
        verify(repositoryRepo, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void searchAndSaveRepositories_WhenGitHubReturnsTooManyRequests_ThrowsRateLimitException() {
        mockGitHubError(HttpStatus.TOO_MANY_REQUESTS);

        GitHubApiException exception = assertThrows(GitHubApiException.class, () ->
                gitHubService.searchAndSaveRepositories(SearchRequestDto.builder().query("spring boot").build()));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @Test
    void searchAndSaveRepositories_WhenGitHubReturnsForbidden_ThrowsRateLimitException() {
        mockGitHubError(HttpStatus.FORBIDDEN);

        GitHubApiException exception = assertThrows(GitHubApiException.class, () ->
                gitHubService.searchAndSaveRepositories(SearchRequestDto.builder().query("spring boot").build()));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockGitHubResponse(GitHubApiResponse response) {
        RestClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GitHubApiResponse.class)).thenReturn(response);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockGitHubError(HttpStatus status) {
        RestClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);
        when(githubRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GitHubApiResponse.class))
                .thenThrow(HttpClientErrorException.create(status, status.getReasonPhrase(), null, null, null));
    }
}
