package com.assignment.githubsearcher.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequestDto {

    @NotBlank(message = "Search query must not be blank")
    @Schema(description = "Repository name or keyword to search", example = "spring boot")
    private String query;

    @Schema(description = "Optional programming language filter", example = "Java")
    private String language;

    @Pattern(regexp = "^(stars|forks|updated)?$", message = "Sort must be one of: stars, forks, updated")
    @Schema(description = "Sort criteria: stars, forks, or updated", example = "stars")
    private String sort;
}
