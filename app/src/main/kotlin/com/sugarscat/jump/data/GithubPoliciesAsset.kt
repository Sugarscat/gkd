package com.sugarscat.jump.data

import kotlinx.serialization.Serializable
import com.sugarscat.jump.util.FILE_SHORT_URL

@Serializable
data class GithubPoliciesAsset(
    val id: Int,
    val href: String,
) {
    val shortHref: String
        get() = FILE_SHORT_URL + id
}
