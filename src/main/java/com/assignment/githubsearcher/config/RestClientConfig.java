package com.assignment.githubsearcher.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${github.api.base-url:https://api.github.com}")
    private String githubBaseUrl;

    @Value("${github.api.token:}")
    private String githubToken;

    @Bean
    public RestClient githubRestClient(RestClient.Builder builder) {
        RestClient.Builder clientBuilder = builder
                .baseUrl(githubBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "github-repo-searcher-app");

        if (StringUtils.hasText(githubToken)) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken);
        }

        return clientBuilder.build();
    }
}
