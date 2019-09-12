package util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import model.*
import okhttp3.*
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

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

fun experimentFiles(): List<Pair<EncodeExperiment, ExperimentFile>> {
    val searchResult = requestEncodeSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }

    return runParallel("Experiment Lookup", experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        val filteredExperiments = experiment.files.filter {
            val url = it.cloudMetadata?.url
            it.isReleased() && it.isReplicatedPeaks() && !url!!.contains("encode-private") && bedGzNumPeaks(url) >= 1000
        }

        filteredExperiments.map { experiment to it }
    }.flatten()
}

/**
 * Downloads the given compressed peaks file (.bed.gz) and checks the number of peaks.
 */
fun bedGzNumPeaks(bedGzUrl: String): Long {
    val request = Request.Builder().url(bedGzUrl).build()
    val response = http.newCall(request).execute()
    return GZIPInputStream(response.body()!!.byteStream()).bufferedReader().lines().count()
}