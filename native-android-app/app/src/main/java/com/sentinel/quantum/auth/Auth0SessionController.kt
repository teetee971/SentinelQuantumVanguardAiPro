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
        credentialsManager.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
            override fun onSuccess(result: Credentials) {
                onSuccess(result.toSummary())
            }

            override fun onFailure(error: CredentialsManagerException) {
                onFailure()
            }
        })
    }

    fun login(
        activity: Activity,
        onSuccess: (Auth0UserSummary) -> Unit,
        onFailure: (String?) -> Unit
    ) {
        WebAuthProvider.login(account)
            .withScheme(configuration.scheme)
            .withAudience(configuration.audience)
            .withScope("openid profile email offline_access")
            .start(activity, object : Callback<Credentials, AuthenticationException> {
                override fun onSuccess(result: Credentials) {
                    credentialsManager.saveCredentials(result)
                    onSuccess(result.toSummary())
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
        WebAuthProvider.logout(account)
            .withScheme(configuration.scheme)
            .start(activity, object : Callback<Void?, AuthenticationException> {
                override fun onSuccess(result: Void?) {
                    credentialsManager.clearCredentials()
                    onSuccess()
                }

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
