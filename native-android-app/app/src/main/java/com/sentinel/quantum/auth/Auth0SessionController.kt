package com.sentinel.quantum.auth

import android.app.Activity
import android.content.Context
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.sentinel.quantum.R

data class Auth0UserSummary(
    val subject: String,
    val email: String?
)

/** Official Auth0 SDK boundary for login, logout and encrypted session restoration. */
class Auth0SessionController(context: Context) {
    private companion object {
        val sessionGuard = AuthSessionGeneration()
    }
    private val appContext = context.applicationContext
    private val configuration = Auth0Configuration(
        domain = appContext.getString(R.string.com_auth0_domain),
        clientId = appContext.getString(R.string.com_auth0_client_id),
        scheme = appContext.getString(R.string.com_auth0_scheme),
        audience = appContext.getString(R.string.com_auth0_audience)
    )

    private val account: Auth0
    private val credentialsManager: SecureCredentialsManager

    init {
        require(configuration.validate().isEmpty()) { configuration.validate().joinToString(",") }
        account = Auth0.getInstance(configuration.clientId, configuration.domain)
        credentialsManager = SecureCredentialsManager(
            appContext,
            account,
            SharedPreferencesStorage(appContext)
        )
    }

    fun restore(
        onSuccess: (Auth0UserSummary) -> Unit,
        onFailure: () -> Unit
    ) {
        val requestGeneration = sessionGuard.current()
        credentialsManager.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
            override fun onSuccess(result: Credentials) {
                if (sessionGuard.isCurrent(requestGeneration)) {
                    onSuccess(result.toSummary())
                } else {
                    onFailure()
                }
            }

            override fun onFailure(error: CredentialsManagerException) {
                sessionGuard.runIfCurrent(requestGeneration) {
                    onFailure()
                }
            }
        })
    }

    /**
     * Reads the current access token from Auth0's encrypted credentials store.
     * The token is delivered only to the in-memory caller and is never persisted here.
     */
    fun accessToken(
        onSuccess: (String) -> Unit,
        onFailure: (String?) -> Unit
    ) {
        val requestGeneration = sessionGuard.current()
        credentialsManager.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
            override fun onSuccess(result: Credentials) {
                if (!sessionGuard.isCurrent(requestGeneration)) {
                    onFailure("AUTH0_SESSION_CANCELLED")
                } else {
                    val token = result.accessToken
                    if (token.isNullOrBlank()) {
                        onFailure("AUTH0_ACCESS_TOKEN_MISSING")
                    } else {
                        onSuccess(token)
                    }
                }
            }

            override fun onFailure(error: CredentialsManagerException) {
                sessionGuard.runIfCurrent(requestGeneration) {
                    onFailure(error.message)
                }
            }
        })
    }

    fun login(
        activity: Activity,
        onSuccess: (Auth0UserSummary) -> Unit,
        onFailure: (String?) -> Unit
    ) {
        val requestGeneration = sessionGuard.begin()
        WebAuthProvider.login(account)
            .withScheme(configuration.scheme)
            .withAudience(configuration.audience)
            .withScope("openid profile email offline_access")
            .start(activity, object : Callback<Credentials, AuthenticationException> {
                override fun onSuccess(result: Credentials) {
                    val accepted = sessionGuard.runIfCurrent(requestGeneration) {
                        credentialsManager.saveCredentials(result)
                    }
                    if (!accepted || !sessionGuard.isCurrent(requestGeneration)) {
                        onFailure("AUTH0_SESSION_CANCELLED")
                    } else {
                        onSuccess(result.toSummary())
                    }
                }

                override fun onFailure(error: AuthenticationException) {
                    onFailure(error.message)
                }
            })
    }

    fun logout(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String?) -> Unit
    ) {
        sessionGuard.invalidateAndRun {
            credentialsManager.clearCredentials()
        }
        AuthSessionRevocation.revoke()
        onSuccess()

        WebAuthProvider.logout(account)
            .withScheme(configuration.scheme)
            .start(activity, object : Callback<Void?, AuthenticationException> {
                override fun onSuccess(result: Void?) = Unit

                override fun onFailure(error: AuthenticationException) {
                    onFailure(error.message)
                }
            })
    }

    private fun Credentials.toSummary(): Auth0UserSummary = Auth0UserSummary(
        subject = user.getId().orEmpty(),
        email = user.email
    )
}
