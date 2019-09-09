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
        val files: List<ExperimentFile>
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

/*
 * Some helper functions
 */
fun ExperimentFile.isReleased() = status == "released"
fun ExperimentFile.isReplicatedPeaks() = fileType == "bed narrowPeak" &&
        (outputType == "replicated peaks" || outputType == "optimal idr thresholded peaks")