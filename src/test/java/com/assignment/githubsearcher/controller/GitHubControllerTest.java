package com.assignment.githubsearcher.controller;

import com.assignment.githubsearcher.dto.RepositoriesResponseDto;
import com.assignment.githubsearcher.dto.RepositoryDto;
import com.assignment.githubsearcher.dto.SearchRequestDto;
import com.assignment.githubsearcher.dto.SearchResponseDto;
import com.assignment.githubsearcher.service.GitHubService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitHubController.class)
class GitHubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GitHubService gitHubService;

    @Test
    void searchRepositories_ShouldReturn200() throws Exception {
        RepositoryDto repoDto = RepositoryDto.builder()
                .id(123456L)
                .name("spring-boot-example")
                .description("An example repository for Spring Boot")
                .owner("user123")
                .language("Java")
                .stars(450)
                .forks(120)
                .lastUpdated(Instant.parse("2024-01-01T12:00:00Z"))
                .build();

        SearchResponseDto mockResponse = SearchResponseDto.builder()
                .message("Repositories fetched and saved successfully")
                .repositories(List.of(repoDto))
                .build();

        when(gitHubService.searchAndSaveRepositories(any(SearchRequestDto.class))).thenReturn(mockResponse);

        SearchRequestDto request = SearchRequestDto.builder()
                .query("spring boot")
                .language("Java")
                .sort("stars")
                .build();

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Repositories fetched and saved successfully"))
                .andExpect(jsonPath("$.repositories[0].name").value("spring-boot-example"));
    }

    @Test
    void searchRepositories_WithBlankQuery_ShouldReturn400() throws Exception {
        SearchRequestDto request = SearchRequestDto.builder()
                .query("")
                .language("Java")
                .sort("stars")
                .build();

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void getRepositories_ShouldReturn200() throws Exception {
        RepositoryDto repoDto = RepositoryDto.builder()
                .id(123456L)
                .name("spring-boot-example")
                .owner("user123")
                .language("Java")
                .stars(450)
                .forks(120)
                .lastUpdated(Instant.parse("2024-01-01T12:00:00Z"))
                .build();

        when(gitHubService.getStoredRepositories("Java", 100, "stars"))
                .thenReturn(RepositoriesResponseDto.builder().repositories(List.of(repoDto)).build());

        mockMvc.perform(get("/api/github/repositories")
                        .param("language", "Java")
                        .param("minStars", "100")
                        .param("sort", "stars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories[0].id").value(123456));
    }
}
