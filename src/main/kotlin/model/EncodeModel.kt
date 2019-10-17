package model

import com.squareup.moshi.Json

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
        @Json(name = "biosample_ontology") val biosampleOntology: BiosampleOntology
)

data class ExperimentFile(
        val accession: String?,
        val assembly: String?,
        val status: String,
        @Json(name = "file_type") val fileType: String,
        @Json(name = "output_type") val outputType: String,
        @Json(name = "cloud_metadata") val cloudMetadata: CloudMetadata?
)

data class CloudMetadata(val url: String)

data class BiosampleOntology(@Json(name = "@id") val id: String)

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
fun ExperimentFile.isReleased() = status.toLowerCase() == "released"
fun ExperimentFile.isReplicatedPeaks() = fileType.toLowerCase() == "bed narrowpeak" &&
        (outputType.toLowerCase() == "replicated peaks" || outputType.toLowerCase() == "optimal idr thresholded peaks")
fun ExperimentFile.isBedMethyl() = fileType.toLowerCase() == "bed bedmethyl" &&
        outputType.toLowerCase() == "methylation state at cpg"
