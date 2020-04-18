package util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import model.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import mu.KotlinLogging

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

fun requestEncodeMethylSearch(): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$ENCODE_BASE_URL/search/")!!.newBuilder()
            .addQueryParameter("searchTerm", "WGBS")
            .addQueryParameter("type", "Experiment")
            .addQueryParameter("status", "released")
            .addQueryParameter("files.file_type", "bed bedMethyl")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "all")
            .build()
    return requestEncodeSearch(searchUrl)
}

fun requestEncodeChipSeqSearch(): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$ENCODE_BASE_URL/search/")!!.newBuilder()
            .addQueryParameter("searchTerm", "ChIP-seq")
            .addQueryParameter("type", "Experiment")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "all")
            .build()
    return requestEncodeSearch(searchUrl)
}

fun requestEncodeExperiment(accession: String): EncodeExperiment {
    val experimentUrl = HttpUrl.parse("$ENCODE_BASE_URL/experiments/$accession/")!!.newBuilder()
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
    val methylExperiment: EncodeExperiment,
    val methylBeds: Set<ExperimentFile>,
    val matchingCriteria: ExperimentMatchCriteria
)

/**
 * Fetches methyl bed files with matching chip seq narrowPeak files
 */
fun methylBedMatches(): List<MethylFileMatch> {
    val chipSeqFiles = chipSeqBedFiles()
    val methylBedFiles = methylBedFiles()
    val methylExperimentScores = mutableMapOf<EncodeExperiment, Double>()

    val methylFilesByMatchCriteria: Map<ExperimentMatchCriteria, Set<EncodeFileWithExp>> = methylBedFiles
            .map { fileWithExp -> fileWithExp.toMatchCriteria() to fileWithExp }
            .filter { it.first != null }
            .groupBy { it.first!! }
            .mapValues { entry -> entry.value.map { it.second }.toSet() }

    return chipSeqFiles.mapNotNull { chipSeqFile ->
        val matchCriteria = chipSeqFile.toMatchCriteria() ?: return@mapNotNull null
        var methylFiles = methylFilesByMatchCriteria[matchCriteria] ?: return@mapNotNull null
        val methylExperiments = methylFiles.map { it.experiment }.toSet()
        if (methylExperiments.size > 1) {
            val maxScoreMethylExperiment = methylExperiments.maxBy { methylExperiment ->
                methylExperimentScores.getOrPut(methylExperiment) { methylExperiment.methylScore() }
            }
            methylFiles = methylFiles.filter { it.experiment == maxScoreMethylExperiment }.toSet()
        }
        MethylFileMatch(chipSeqFile, methylFiles.first().experiment, methylFiles.map { it.file }.toSet(), matchCriteria)
    }
}

private fun EncodeExperiment.methylScore(): Double {
    val bamsByReplicate = this.files
            .filter { it.isBam() && it.biologicalReplicates.size == 1 }
            .groupBy { it.biologicalReplicates.first() }
    val repScores = bamsByReplicate.values.map { repBams ->
        repBams.filter { bam ->
            bam.qualityMetrics?.firstOrNull()?.mapped !== null
        }.map {
            bam -> bam.qualityMetrics!!.firstOrNull()!!.mapped
        }.sumByDouble { it!!.split("%")[0].toDouble() }
    }
    return repScores.average()
}

// Aggregate
fun requestEncodeHistoneSearch(): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$ENCODE_BASE_URL/search/")!!.newBuilder()
            .addEncodedQueryParameter("searchTerm", "Histone+ChIP-seq")
            .addQueryParameter("type", "Experiment")
            .addQueryParameter("status", "released")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "all")
            .build()
    log.info { "Searching for histone using url: $searchUrl" }
    return requestEncodeSearch(searchUrl)
}

fun requestEncodeDnaseSearch(): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$ENCODE_BASE_URL/search/")!!.newBuilder()
            .addQueryParameter("assay_title", "DNase-seq")
            .addQueryParameter("type", "Experiment")
            .addQueryParameter("status", "released")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "all")
            .build()
    log.info { "Searching for Dnase using url: $searchUrl" }
    return requestEncodeSearch(searchUrl)
}

fun requestEncodeFileWithExps(
    name: String,
    experimentAccessions: List<String>,
    fileFilter: (exp: EncodeExperiment, file: ExperimentFile) -> Boolean
): List<EncodeFileWithExp> {
    return runParallel(name, experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        if (experiment.hasTreatments()) {
            emptySet<EncodeFileWithExp>()
        } else {
            val filteredExperiments = experiment.files.filter {
                val url = it.cloudMetadata?.url
                it.isReleased() && url != null && !url.contains("encode-private") && fileFilter(experiment, it)
            }

            filteredExperiments.map { EncodeFileWithExp(it, experiment) }
        }
    }.flatten()
}

fun histoneSignalFiles(): List<EncodeFileWithExp> {
    val searchResult = requestEncodeHistoneSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }

    return requestEncodeFileWithExps("Histone Experiment Lookup", experimentAccessions) { exp,file ->
        isReplicatedFoldChangeOverControl(exp, file)
    }
}

fun dnaseAlignmentFiles(): List<EncodeFileWithExp> {
    val searchResult = requestEncodeDnaseSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }

    return requestEncodeFileWithExps("Dnase Experiment Lookup", experimentAccessions) { exp,file ->
        isFirstAlignments(exp, file)
    }
}
