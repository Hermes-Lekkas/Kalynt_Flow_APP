package com.example.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Response

data class GitHubRepo(
    val id: Long,
    val name: String,
    val full_name: String,
    val private: Boolean,
    val description: String?,
    val open_issues_count: Int
)

data class GitHubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val html_url: String,
    val pull_request: Any?,
    val user: GitHubUser,
    val assignees: List<GitHubUser>,
    val labels: List<GitHubLabel>,
    val created_at: String?
)

data class GitHubUser(
    val login: String,
    val avatar_url: String
)

data class GitHubLabel(
    val name: String,
    val color: String
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
