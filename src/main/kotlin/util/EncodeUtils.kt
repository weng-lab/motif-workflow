package util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import model.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.text.ParseException
import java.util.*

const val ENCODE_BASE_URL = "https://www.encodeproject.org/"

private val http by lazy {
    OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
}
private val moshi by lazy {
    Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(customDateAdapter())
            .build()
}
class customDateAdapter {
    val dateFormat: DateFormat
    init {
        dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
    }
    @ToJson
    @Synchronized
    fun dateToJson(d: Date): String {
        return dateFormat.format(d)
    }

    @FromJson
    @Synchronized
    @Throws(ParseException::class)
    fun dateToJson(s: String): Date {
        return dateFormat.parse(s)
    }
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

fun requestEncodeATACSearch(): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$ENCODE_BASE_URL/search/")!!.newBuilder()
            .addQueryParameter("searchTerm", "ATAC-seq")
            .addQueryParameter("type", "Experiment")
            .addQueryParameter("status", "released")
            .addQueryParameter("files.output_type", "alignments")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "all")
            .build()
    return requestEncodeSearch(searchUrl)
}

fun requestEncodeDNaseSearch(): EncodeSearchResult {
    val searchUrl = HttpUrl.parse("$ENCODE_BASE_URL/search/")!!.newBuilder()
            .addQueryParameter("searchTerm", "DNase-seq")
            .addQueryParameter("type", "Experiment")
            .addQueryParameter("status", "released")
            .addQueryParameter("files.output_type", "alignments")
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

    val exps = runParallel("ChipSeq Experiment Lookup", experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        val filteredFilesforExp = experiment.files.filter {
            val url = it.cloudMetadata?.url
            it.isReleased() && it.isReplicatedPeaks() && !url!!.contains("encode-private") && bedGzNumPeaks(url) >= 1000
        }
        val filteredPeakfilesforExp = filteredFilesforExp.filter {
            it.isPeak()
        }

        //max of technical replicates
        val mreplicates = filteredPeakfilesforExp.map { it->
            it.technicalReplicates.size
        }.max()

        var best_files = filteredPeakfilesforExp.filter { it->
            it.outputType.lowercase().contains("optimal")
        }
        if(best_files.size==0) {
            best_files = filteredPeakfilesforExp.filter { it->
                it.outputType.lowercase().contains("pseudoreplicated")
            }
        }
        if(best_files.size==0) {
            best_files = filteredPeakfilesforExp
        }

        val mostRecentFiles =  best_files.filter {it-> it.technicalReplicates.size==mreplicates }.sortedByDescending { it.dateCreated }

        var expFile = emptyList<ExperimentFile>()
        if(mostRecentFiles.size>0){
            expFile = listOf(mostRecentFiles[0])
        }
        expFile.map { EncodeFileWithExp(it, experiment)}


    }.flatten()

    return exps
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

fun atacSeqBamFiles(): List<EncodeFileWithExp> {
    val searchResult = requestEncodeATACSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }

    return runParallel("ATAC Experiment Lookup", experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        val filteredExperiments = experiment.files.filter {
            val url = it.cloudMetadata?.url
            it.isReleased() && it.isAlignments() && !url!!.contains("encode-private")
        }

        filteredExperiments.map { EncodeFileWithExp(it, experiment) }
    }.flatten()
}

fun dnaseSeqBamFiles(): List<EncodeFileWithExp> {
    val searchResult = requestEncodeDNaseSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }

    return runParallel("DNase Experiment Lookup", experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        val filteredExperiments = experiment.files.filter {
            val url = it.cloudMetadata?.url
            it.isReleased() && it.isAlignments() && !url!!.contains("encode-private")
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

data class ATACMatch(
    val chipSeqFile: EncodeFileWithExp,
    val atacExperiment: EncodeExperiment,
    val atacBams: Set<ExperimentFile>,
    val matchingCriteria: ExperimentMatchCriteria
)

data class DNaseMatch(
    val chipSeqFile: EncodeFileWithExp,
    val dnaseExperiment: EncodeExperiment,
    val bestDnaseBam: ExperimentFile,
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

/**
 * Fetches ATAC-seq filtered alignemnt files with matching chip seq narrowPeak files
 */
fun atacAlignmentMatches(): Map<String, ATACMatch> {
    val chipSeqFiles = chipSeqBedFiles()
    val atacFiles = atacSeqBamFiles()
    val atacExperimentScores = mutableMapOf<EncodeExperiment, Double>()

    val atacFilesByMatchCriteria: Map<ExperimentMatchCriteria, Set<EncodeFileWithExp>> = atacFiles
            .map { fileWithExp -> fileWithExp.toMatchCriteria() to fileWithExp }
            .filter { it.first != null }
            .groupBy { it.first!! }
            .mapValues { entry -> entry.value.map { it.second }.toSet() }

    return chipSeqFiles.mapNotNull { chipSeqFile ->
        val matchCriteria = chipSeqFile.toMatchCriteria() ?: return@mapNotNull null
        var atacSeqFiles = atacFilesByMatchCriteria[matchCriteria] ?: return@mapNotNull null
        val atacSeqExperiments = atacSeqFiles.map { it.experiment }.toSet()
        if (atacSeqExperiments.size > 1) {
            val maxScoreATACExperiment = atacSeqExperiments.maxBy { atacSeqExperiment ->
                atacExperimentScores.getOrPut(atacSeqExperiment) { atacSeqExperiment.atacScore() }
            }
            atacSeqFiles = atacSeqFiles.filter { it.experiment == maxScoreATACExperiment }.toSet()
        }
        ATACMatch(chipSeqFile, atacSeqFiles.first().experiment, atacSeqFiles.map { it.file }.toSet(), matchCriteria)
    }.associateBy { it.chipSeqFile.file.accession!! }
}

/**
 * Fetches ATAC-seq filtered alignemnt files with matching chip seq narrowPeak files
 */
fun dnaseAlignmentMatches(): Map<String, DNaseMatch> {
    val chipSeqFiles = chipSeqBedFiles()
    val dnaseFiles = atacSeqBamFiles()
    val dnaseExperimentScores = mutableMapOf<EncodeExperiment, Double>()

    val dnaseFilesByMatchCriteria: Map<ExperimentMatchCriteria, Set<EncodeFileWithExp>> = dnaseFiles
        .map { fileWithExp -> fileWithExp.toMatchCriteria() to fileWithExp }
        .filter { it.first != null }
        .groupBy { it.first!! }
        .mapValues { entry -> entry.value.map { it.second }.toSet() }

    return chipSeqFiles.mapNotNull { chipSeqFile ->
        val matchCriteria = chipSeqFile.toMatchCriteria() ?: return@mapNotNull null
        var dnaseSeqFiles = dnaseFilesByMatchCriteria[matchCriteria] ?: return@mapNotNull null
        val dnaseSeqExperiments = dnaseSeqFiles.map { it.experiment }.filter { it.bestDNaseBAM(chipSeqFile.file.assembly) !== null }.toSet()
        if (dnaseSeqExperiments.size > 1) {
            val maxScoreDNaseExperiment = dnaseSeqExperiments.maxBy { dnaseSeqExperiment ->
                dnaseExperimentScores.getOrPut(dnaseSeqExperiment) { dnaseSeqExperiment.dnaseScore() }
            }
            dnaseSeqFiles = dnaseSeqFiles.filter { it.experiment == maxScoreDNaseExperiment }.toSet()
        }
	if (dnaseSeqFiles.firstOrNull() === null) return@mapNotNull null
        DNaseMatch(chipSeqFile, dnaseSeqFiles.first().experiment, dnaseSeqFiles.first().experiment.bestDNaseBAM(chipSeqFile.file.assembly)!!, matchCriteria)
    }.associateBy { it.chipSeqFile.file.accession!! }
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
        }.sumOf { it!!.split("%")[0].toDouble() }
    }
    return repScores.average()
}

private fun EncodeExperiment.atacScore(): Double {
    val bamsByReplicate = this.files
        .filter { it.isAlignments() && it.biologicalReplicates.size == 1 }
        .groupBy { it.biologicalReplicates.first() }
    val repScores = bamsByReplicate.values.map { repBams ->
        repBams.filter { bam ->
            bam.qualityMetrics?.firstOrNull()?.mapped !== null
        }.map {
            bam -> bam.qualityMetrics!!.firstOrNull()!!.mapped
        }.sumOf { it!!.split("%")[0].toDouble() }
    }
    return repScores.average()
}

private fun EncodeExperiment.bestDNaseBAM(assembly: String? = null): ExperimentFile? {
    return this.files
        .filter { it.isAlignments() && it.biologicalReplicates.size == 1 && (assembly === null || it.assembly === assembly)}
        .sortedBy { it.biologicalReplicates.first() }.firstOrNull()
}

private fun EncodeExperiment.dnaseScore(): Double {
    val bam = this.bestDNaseBAM()
    if (bam?.qualityMetrics?.firstOrNull()?.mapped === null) return 0.0
    return bam.qualityMetrics.firstOrNull()!!.mapped!!.split("%")[0].toDouble()
}
