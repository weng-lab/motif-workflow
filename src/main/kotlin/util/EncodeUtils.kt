package util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import model.*
import okhttp3.*
import java.util.concurrent.TimeUnit

const val ENCODE_BASE_URL = "https://www.encodeproject.org/"

private val http by lazy {
    OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
}
private val moshi by lazy {
    Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
}

fun requestEncodeSearch(encodeBaseUrl: String = ENCODE_BASE_URL): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$encodeBaseUrl/search/")!!.newBuilder()
            .addQueryParameter("searchTerm", "ChIP-seq")
            .addQueryParameter("type", "experiment")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "all")
            .build()

    val searchRequest = Request.Builder().url(searchUrl).get().build()

    val searchResultString = http.newCall(searchRequest).execute().body()!!.string()
    return moshi.adapter(EncodeSearchResult::class.java).fromJson(searchResultString)!!
}

fun requestEncodeExperiment(accession: String, encodeBaseUrl: String = ENCODE_BASE_URL): EncodeExperiment {
    val experimentUrl = HttpUrl.parse("$encodeBaseUrl/experiments/$accession/")!!.newBuilder()
            .addQueryParameter("format", "json")
            .build()
    val experimentRequest = Request.Builder().url(experimentUrl).get().build()
    val experimentResultString = retry("Experiment Request", 3) {
        http.newCall(experimentRequest).execute().body()!!.string()
    }
    return moshi.adapter(EncodeExperiment::class.java).fromJson(experimentResultString)!!
}
