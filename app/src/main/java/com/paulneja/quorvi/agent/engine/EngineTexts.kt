package com.paulneja.quorvi.agent.engine

import android.content.Context
import com.paulneja.quorvi.R

interface EngineTexts {
    fun noModel(): String
    fun blocked(): String
    fun unreachable(detail: String): String
    fun badKey(status: Int): String
    fun rateLimited(): String
    fun providerError(status: Int): String
    fun timeout(): String
    fun failed(detail: String): String

    object English : EngineTexts {
        override fun noModel() = "No model is set up. Add a provider in Settings → AI and try again."
        override fun blocked() = "Blocked:"
        override fun unreachable(detail: String) = "Couldn't reach the model: $detail"
        override fun badKey(status: Int) = "The provider rejected the key (HTTP $status). Check it in Settings → AI."
        override fun rateLimited() = "The provider is rate limiting requests (429). Try again in a while or add a backup model."
        override fun providerError(status: Int) = "The provider answered with an error (HTTP $status)."
        override fun timeout() = "The model took too long to answer."
        override fun failed(detail: String) = "Something went wrong: $detail"
    }
}

class AndroidEngineTexts(private val context: Context) : EngineTexts {
    override fun noModel() = context.getString(R.string.engine_no_model)
    override fun blocked() = context.getString(R.string.engine_blocked)
    override fun unreachable(detail: String) = context.getString(R.string.engine_unreachable, detail)
    override fun badKey(status: Int) = context.getString(R.string.engine_bad_key, status)
    override fun rateLimited() = context.getString(R.string.engine_rate_limited)
    override fun providerError(status: Int) = context.getString(R.string.engine_provider_error, status)
    override fun timeout() = context.getString(R.string.engine_timeout)
    override fun failed(detail: String) = context.getString(R.string.engine_failed, detail)
}
