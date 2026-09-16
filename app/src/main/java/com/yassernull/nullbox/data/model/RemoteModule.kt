package com.yassernull.nullbox.data.model

import kotlinx.serialization.Serializable

// يمثل وحدة (Module) كما هي معروضة في المستودع عن بعد.
@Serializable
data class RemoteModule(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val versionCode: String,
    val repository: String,
    val iconPath: String? = null,
    val readmeUrl: String? = null,
    val downloads: Long = 0
)