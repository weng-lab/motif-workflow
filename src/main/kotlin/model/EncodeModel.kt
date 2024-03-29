package model

import com.squareup.moshi.Json
import java.util.*
/*
 * Search Results Model
 */
data class EncodeSearchResult(@Json(name = "@graph") val graph: List<SearchGraphEntry>)
data class SearchGraphEntry(val accession: String)
 
/*
 * Experiment Metadata Results Model
 */
data class EncodeExperiment(
    val accession: String,
    val files: List<ExperimentFile>,
    val replicates: List<ExperimentReplicate>,
    @Json(name = "biosample_ontology") val biosampleOntology: BiosampleOntology,
    val target: ExperimentTarget?
)

data class ExperimentTarget(val label: String)

data class QualityMetrics(
    @Json(name = "@type") val type: List<String>,
    @Json(name = "mapped_pct") val mapped: String?
)

data class ExperimentFile(
    val accession: String?,
    val assembly: String?,
    val status: String,
    @Json(name = "file_type") val fileType: String,
    @Json(name = "output_type") val outputType: String,
    @Json(name = "technical_replicates") val technicalReplicates: List<String>,
    @Json(name = "cloud_metadata") val cloudMetadata: CloudMetadata?,
    @Json(name = "biological_replicates") val biologicalReplicates: List<Int>,
    @Json(name = "quality_metrics") val qualityMetrics: List<QualityMetrics>?,
    @Json(name = "date_created") val dateCreated: Date?
)

data class CloudMetadata(val url: String)

data class BiosampleOntology(
    @Json(name = "@id") val id: String,
    @Json(name = "term_name") val termName: String
    )

data class ExperimentReplicate(val library: ExperimentLibrary?, val libraries: List<ExperimentLibrary>?)
data class ExperimentLibrary(val biosample: ExperimentBiosample)
data class ExperimentBiosample(
    @Json(name = "@id") val id: String,
    val donor: ExperimentDonor,
    val age: String,
    @Json(name = "age_units") val ageUnits: String?,
    @Json(name = "life_stage") val lifeStage: String
)
data class ExperimentDonor(@Json(name = "@id") val id: String)

/*
 * Some helper functions
 */
fun ExperimentFile.isReleased() = status.lowercase() == "released"
fun ExperimentFile.isReplicatedPeaks() = fileType.lowercase() == "bed narrowpeak" &&
        (outputType.lowercase() == "replicated peaks" || outputType.lowercase() == "optimal idr thresholded peaks"
                || outputType.lowercase() == "pseudoreplicated idr thresholded peaks" || outputType.lowercase() == "pseudoreplicated peaks" || outputType.lowercase() == "idr thresholded peaks")
fun ExperimentFile.isBedMethyl() = fileType.lowercase() == "bed bedmethyl" &&
        outputType.lowercase() == "methylation state at cpg"
fun ExperimentFile.isBam() = fileType.lowercase() == "bam"
fun ExperimentFile.isAlignments() = outputType.lowercase() == "alignments"
fun ExperimentFile.isPeak() = assembly!=null && ( outputType.lowercase().contains("idr") || outputType.lowercase().contains("replicated") ) && technicalReplicates !== null && !fileType.lowercase().contains("big")