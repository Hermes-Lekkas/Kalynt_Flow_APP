package com.example.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Response

data class GitHubRepo(
    val id: Long = 0L,
    val name: String = "",
    val full_name: String = "",
    val private: Boolean = false,
    val description: String? = null,
    val open_issues_count: Int = 0
)

data class GitHubIssue(
    val id: Long = 0L,
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "open",
    val html_url: String = "",
    val pull_request: Any? = null,
    val user: GitHubUser? = null,
    val assignees: List<GitHubUser> = emptyList(),
    val labels: List<GitHubLabel> = emptyList(),
    val created_at: String? = null
)

data class GitHubUser(
    val login: String = "",
    val avatar_url: String = ""
)

data class GitHubLabel(
    val name: String = "",
    val color: String = ""
)

interface GitHubApiService {
    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") authHeader: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<GitHubRepo>>

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getRepoIssues(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<GitHubIssue>>
}
