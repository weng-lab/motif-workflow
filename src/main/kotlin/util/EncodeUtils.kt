package util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import model.*
import mu.KotlinLogging
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream


private val log = KotlinLogging.logger {}

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

private fun requestEncodeSearch(searchUrl: HttpUrl): EncodeSearchResult {
    val searchRequest = Request.Builder().url(searchUrl).get().build()

    val searchResultString = http.newCall(searchRequest).execute().body()!!.string()
    return moshi.adapter(EncodeSearchResult::class.java).fromJson(searchResultString)!!
}

fun requestEncodeMethylSearch(encodeBaseUrl: String = ENCODE_BASE_URL): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$encodeBaseUrl/search/")!!.newBuilder()
            .addQueryParameter("searchTerm", "WGBS")
            .addQueryParameter("type", "Experiment")
            .addQueryParameter("status", "released")
            .addQueryParameter("files.file_type", "bed bedMethyl")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "all")
            .build()
    return requestEncodeSearch(searchUrl)
}

fun requestEncodeChipSeqSearch(encodeBaseUrl: String = ENCODE_BASE_URL): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$encodeBaseUrl/search/")!!.newBuilder()
            .addQueryParameter("searchTerm", "ChIP-seq")
            .addQueryParameter("type", "Experiment")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "all")
            .build()
    return requestEncodeSearch(searchUrl)
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

data class EncodeFileWithExp(val file: ExperimentFile, val experiment: EncodeExperiment)

fun chipSeqBedFiles(): List<EncodeFileWithExp> {
    val searchResult = requestEncodeChipSeqSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }

    return runParallel("ChipSeq Experiment Lookup", experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        val filteredExperiments = experiment.files.filter {
            val url = it.cloudMetadata?.url
            it.isReleased() && it.isReplicatedPeaks() && !url!!.contains("encode-private") && bedGzNumPeaks(url) >= 1000
        }

        filteredExperiments.map { EncodeFileWithExp(it, experiment) }
    }.flatten()
}

fun methylBedFiles(): List<EncodeFileWithExp> {
    val searchResult = requestEncodeMethylSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }

    return runParallel("Methyl Experiment Lookup", experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        val filteredExperiments = experiment.files.filter {
            val url = it.cloudMetadata?.url
            it.isReleased() && it.isBedMethyl() && !url!!.contains("encode-private")
        }

        filteredExperiments.map { EncodeFileWithExp(it, experiment) }
    }.flatten()
}

/**
 * Class representing fields to match experiments by
 * (Hashable and therefor matchable by all fields because data class)
 */
data class ExperimentMatchCriteria(
        val bioSampleOntologyId: String,
        val assembly: String,
        val donorId: String,
        val age: String,
        val ageUnits: String?,
        val lifeStage: String
)

fun EncodeFileWithExp.toMatchCriteria(): ExperimentMatchCriteria? {
    val bioSampleOntologyId = this.experiment.biosampleOntology.id
    val assembly = this.file.assembly?: return null
    for (rep in this.experiment.replicates) {
        if (rep.library != null){
            return rep.library.biosample.toMatchCriteria(bioSampleOntologyId, assembly)
        }
        if (rep.libraries != null && rep.libraries.isNotEmpty()) {
            return rep.libraries[0].biosample.toMatchCriteria(bioSampleOntologyId, assembly)
        }
    }
    return null
}

fun ExperimentBiosample.toMatchCriteria(bioSampleOntologyId: String, assembly: String) =
        ExperimentMatchCriteria(bioSampleOntologyId, assembly, this.donor.id, this.age, this.ageUnits, this.lifeStage)

/**
 * Create Map of EncodeFiles (With Experiments) by bio-sample matching criteria
 */
fun encodeFilesByMatchCriteria(files: Iterable<EncodeFileWithExp>): Map<ExperimentMatchCriteria, Set<EncodeFileWithExp>> =
    files.map { fileWithExp -> fileWithExp.toMatchCriteria() to fileWithExp }
            .filter { it.first != null }
            .groupBy { it.first!! }
            .mapValues { entry -> entry.value.map { it.second }.toSet() }

/**
 * Downloads the given compressed peaks file (.bed.gz) and checks the number of peaks.
 */
fun bedGzNumPeaks(bedGzUrl: String): Long {
    val request = Request.Builder().url(bedGzUrl).build()
    return retry("Peaks File Request (for size check)", 3) {
        val response = http.newCall(request).execute()
        GZIPInputStream(response.body()!!.byteStream()).bufferedReader().lines().count()
    }
}

data class MethylFileMatch(
        val chipSeqFile: EncodeFileWithExp,
        val methylBedFile: EncodeFileWithExp,
        val matchingCriteria: ExperimentMatchCriteria
)

/**
 * Fetches methyl bed files with matching chip seq narrowPeak files
 */
fun methylBedMatches(): List<MethylFileMatch> {
    val chipSeqFiles = chipSeqBedFiles()
    val methylBedFiles = methylBedFiles()

    val chipSeqFilesByMatchCriteria: Map<ExperimentMatchCriteria, Set<EncodeFileWithExp>> = encodeFilesByMatchCriteria(chipSeqFiles)
    val methylFilesByMatchCriteria: Map<ExperimentMatchCriteria, Set<EncodeFileWithExp>> = encodeFilesByMatchCriteria(methylBedFiles)

    val matchingMethylFiles = mutableListOf<MethylFileMatch>()
    // This set is used to make sure we don't have duplicate Methyl Bed - Chip Seq pairs in our result
    val methylChipSeqPairs = mutableSetOf<Pair<EncodeFileWithExp, EncodeFileWithExp>>()
    val bioOntologyMethylBedPairs = methylFilesByMatchCriteria.flatMap { entry -> entry.value.map { entry.key to it } }
    for ((methylFileCriteria, methylFile) in bioOntologyMethylBedPairs) {
        if (chipSeqFilesByMatchCriteria.containsKey(methylFileCriteria)) {
            val boChipSeqFiles = chipSeqFilesByMatchCriteria.getValue(methylFileCriteria)
            for (chipSeqFile in boChipSeqFiles) {
                // Make sure we haven't already added this pair
                if (methylChipSeqPairs.contains(methylFile to chipSeqFile)) continue

                methylChipSeqPairs += methylFile to chipSeqFile
                matchingMethylFiles += MethylFileMatch(chipSeqFile, methylFile, methylFileCriteria)
            }
        }
    }

    return matchingMethylFiles
}