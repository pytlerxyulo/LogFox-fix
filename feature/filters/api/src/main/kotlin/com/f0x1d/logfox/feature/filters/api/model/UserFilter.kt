package com.f0x1d.logfox.feature.filters.api.model

import com.f0x1d.logfox.core.recycler.Identifiable
import com.f0x1d.logfox.core.utils.GsonSkip
import com.f0x1d.logfox.feature.logging.api.model.LogLevel
import com.google.gson.annotations.SerializedName

data class UserFilter(
    @GsonSkip override val id: Long = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("including") val including: Boolean = true,
    @SerializedName("allowedLevels") val allowedLevels: List<LogLevel> = emptyList(),
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("pid") val pid: String? = null,
    @SerializedName("tid") val tid: String? = null,
    @SerializedName("packageName") val packageName: String? = null,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("content") val content: String? = null,
    @GsonSkip val enabled: Boolean = true,
) : Identifiable
