package com.yassernull.modulesbox.data.model

import kotlinx.serialization.Serializable

// يمثل وحدة (Module) كما هي معروضة في المستودع عن بعد.
//
// Fields come straight from the store index (repository.json). What only the GitHub API
// could provide (download counts) is deliberately absent: a row must be renderable from
// the index alone.
//
// The two icon fields are not the same thing and must not be confused:
//  - [icon] is the repo-relative path the index declares (e.g. "icon.jpg"). It is what
//    gets turned into a download URL.
//  - [iconPath] is the absolute path of the cached copy on disk, filled in once the
//    download succeeds. Null means "not fetched yet", and the UI shows a letter avatar.
@Serializable
data class RemoteModule(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val versionCode: String,
    val repository: String,
    val icon: String? = null,
    val iconPath: String? = null,
    val readmeUrl: String? = null
)
