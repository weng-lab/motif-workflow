import krews.core.*
import krews.file.*
import krews.run
import reactor.core.publisher.*
import task.*
import util.*
import java.nio.file.Files

fun main(args: Array<String>) = run(motifWorkflow, args)

data class MotifWorkflowParams(val methylMode: Boolean = false)

val rDHS_FILES = mapOf(
    "GRCh38" to "http://gcp.wenglab.org/GRCh38-rDHSs.bed",
    "mm10" to "http://gcp.wenglab.org/mm10-rDHSs.bed"
)

val motifWorkflow = workflow("motif-workflow") {
    val params = params<MotifWorkflowParams>()
    if (params.methylMode) {
        runForMethylBed()
    } else {
        runForChipSeq()
    }
}

fun WorkflowBuilder.runForChipSeq() {
    val experimentFiles = chipSeqBedFiles()

    // Write peaks file & experiment accessions out to metadata file
    val metadataPath = Files.createTempFile("metadata", ".tsv")
    writeMetadataFile(metadataPath, experimentFiles)
    uploadFile(metadataPath, "outputs/metadata.tsv")

    // Set up motifs task for each peaks file
    val motifsInputs = experimentFiles.map { (experimentFile, _) ->
        MotifsInput(
            peaksBedGz = HttpInputFile(experimentFile.cloudMetadata!!.url, "${experimentFile.accession}.bed.gz"),
            assemblyTwoBit = HttpInputFile(assemblyUrl(experimentFile.assembly!!), "${experimentFile.assembly}.2bit"),
            chromSizes = HttpInputFile(chromeSizesUrl(experimentFile.assembly), "${experimentFile.assembly}.chrom.sizes"),
            rDHSs = null /* if (experimentFile.assembly in rDHS_FILES)
                HttpInputFile(rDHS_FILES.getValue(experimentFile.assembly), "${experimentFile.assembly}-rDHSs.bed")
            else null */
        )
    }.toFlux()
    val motifTask = motifsTask("meme",motifsInputs)

    // perform TOMTOM on discovered motifs
    val tomTomInputs = motifTask.map {
        TomTomInput(
            queryMotif = it.motifsXml
        )
    }
    tomTomTask("tomtom",tomTomInputs)
    
}

fun WorkflowBuilder.runForMethylBed() {
    val methylMatches = methylBedMatches()

    val metadataPath = Files.createTempFile("metadata", ".tsv")
    writeMethylMetadataFile(metadataPath, methylMatches)
    uploadFile(metadataPath, "outputs/metadata.tsv")

    val motifsInputs = methylMatches.map { methylBedMatch ->
        val peaksFile = methylBedMatch.chipSeqFile.file
        val methylBeds = methylBedMatch.methylBeds.map {
            HttpInputFile(it.cloudMetadata!!.url, "${it.accession}.bed.gz")
        }
        MotifsInput(
            peaksBedGz = HttpInputFile(peaksFile.cloudMetadata!!.url, "${peaksFile.accession}.bed.gz"),
            assemblyTwoBit = HttpInputFile(assemblyUrl(peaksFile.assembly!!), "${peaksFile.assembly}.2bit"),
            chromSizes = HttpInputFile(chromeSizesUrl(peaksFile.assembly), "${peaksFile.assembly}.chrom.sizes"),
            methylBeds = methylBeds,
            rDHSs = if (peaksFile.assembly in rDHS_FILES)
                HttpInputFile(rDHS_FILES.getValue(peaksFile.assembly), "${peaksFile.assembly}-rDHSs.bed")
            else null
        )
    }.toFlux()
    motifsTask("meme",motifsInputs)
}
