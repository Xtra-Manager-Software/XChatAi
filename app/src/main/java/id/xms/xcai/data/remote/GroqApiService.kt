package id.xms.xcai.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface GroqApiService {

    @POST("openai/v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GroqChatRequest
    ): GroqChatResponse

    companion object {
        private const val BASE_URL = "https://api.groq.com/"

        fun create(): GroqApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GroqApiService::class.java)
        }
    }
}
