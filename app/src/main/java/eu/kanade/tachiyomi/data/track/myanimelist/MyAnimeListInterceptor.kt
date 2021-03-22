package eu.kanade.tachiyomi.data.track.myanimelist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import uy.kohesive.injekt.injectLazy

class MyAnimeListInterceptor(private val myanimelist: MyAnimeList, private var token: String?) : Interceptor {

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val json: Json by injectLazy()

    private var oauth: OAuth? = null
        set(value) {
            field = value?.copy(expires_in = System.currentTimeMillis() + (value.expires_in * 1000))
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (token.isNullOrEmpty()) {
            throw Exception("Not authenticated with MyAnimeList")
        }
        if (oauth == null) {
            oauth = myanimelist.loadOAuth()
        }
        // Refresh access token if null or expired.
        if (oauth!!.isExpired()) {
            chain.proceed(MyAnimeListApi.refreshTokenRequest(oauth!!.refresh_token)).use {
                if (it.isSuccessful) {
                    setAuth(json.decodeFromString(it.body!!.string()))
                }
            }
        }

        // Throw on null auth.
        if (oauth == null) {
            throw Exception("No authentication token")
        }

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer ${oauth!!.access_token}")
                .build()

        return chain.proceed(authRequest)
    }

    /**
     * Called when the user authenticates with MyAnimeList for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: OAuth?) {
        token = oauth?.access_token
        this.oauth = oauth
        myanimelist.saveOAuth(oauth)
    }
}
