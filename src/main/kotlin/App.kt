import krews.core.*
import krews.file.*
import krews.run
import reactor.core.publisher.*
import task.*
import util.*
import model.*
import java.nio.file.Files

fun main(args: Array<String>) = run(motifWorkflow, args)

data class MotifWorkflowParams(
    val methylMode: Boolean = false,
    val genomeTarMap: Map<String, File>,
    val twoBitMap: Map<String, File>?,
    val chromSizesMap: Map<String, File>?,
    val conservationMap: Map<String, SignalFile>?
)

val rDHS_FILES = mapOf(
    "GRCh38" to "http://gcp.wenglab.org/GRCh38-rDHSs.bed",
    "mm10" to "http://gcp.wenglab.org/mm10-rDHSs.bed"
)

val motifWorkflow = workflow("motif-workflow") {
    val params = params<MotifWorkflowParams>()
    if (params.methylMode) {
        runForMethylBed()
    } else {
        runForChipSeq(params.genomeTarMap, params.twoBitMap, params.chromSizesMap, params.conservationMap)
    }
}

fun WorkflowBuilder.runForChipSeq(genomeMap: Map<String, File>, twoBitMap: Map<String, File>?, chromSizesMap: Map<String, File>?, conservationMap: Map<String, SignalFile>?) {
    val experimentFiles = chipSeqBedFiles()

    // Write peaks file & experiment accessions out to metadata file
    val metadataPath = Files.createTempFile("metadata", ".tsv")
    writeMetadataFile(metadataPath, experimentFiles)
    uploadFile(metadataPath, "outputs/metadata.tsv")

    // Set up motifs task for each peaks file
    val motifsInputs = experimentFiles.map { (experimentFile, _) ->
        MotifsInput(
            peaksBedGz = HttpInputFile(experimentFile.cloudMetadata!!.url, "${experimentFile.accession}.bed.gz", "dockerhub.wenglab.org/pratth/alpine:latest"),
            assemblyTwoBit = twoBitMap?.get(experimentFile.assembly!!) ?: HttpInputFile(assemblyUrl(experimentFile.assembly!!), "${experimentFile.assembly}.2bit", "dockerhub.wenglab.org/pratth/alpine:latest"),
            chromSizes = chromSizesMap?.get(experimentFile.assembly!!) ?: HttpInputFile(chromeSizesUrl(experimentFile.assembly!!), "${experimentFile.assembly}.chrom.sizes", "dockerhub.wenglab.org/pratth/alpine:latest"),
	    assembly = experimentFile.assembly!!
        )
    }.toFlux()
    val motifTask = motifsTask("meme",motifsInputs)

    // Conservation aggregation
    val conservationInput = motifTask.filter { conservationMap?.get(it.assembly) !== null }.map {
        AggregateInput(
	    it.occurrencesTsv,
	    conservationMap!!.get(it.assembly)!!,
	    it.occurrencesTsv.filename().split('.')[0]
	)
    }
    aggregateTask("conservation", conservationInput)

    // Find ATAC-seq BAM matches
    /* val atacSeqFiles = atacAlignmentMatches()
    val atacMetadataPath = Files.createTempFile("atac-metadata", ".tsv")
    writeATACMetadataFile(atacMetadataPath, atacSeqFiles.values.toList())
    uploadFile(atacMetadataPath, "outputs/atac-metadata.tsv")
    val atacSeqAggregateInput = motifTask.filter {
        atacSeqFiles.containsKey(it.occurrencesTsv.filename().split('.')[0])
            && atacSeqFiles.get(it.occurrencesTsv.filename().split('.')[0])!!.atacBams.size > 0
    }.map {
        val bam = atacSeqFiles.get(it.occurrencesTsv.filename().split('.')[0])!!.atacBams.first()
        val assembly = bam.assembly!!
        ATACAggregateInput(
            it.occurrencesTsv,
            HttpInputFile(bam.cloudMetadata!!.url, "${bam.accession}.bam", "dockerhub.wenglab.org/pratth/alpine:latest"),
            if (assembly == "GRCh38") "hg38" else "GRCh38", genomeMap.get(if (assembly == "GRCh38") "hg38" else "GRCh38")
        )
    }
    atacAggregateTask("atacseqaggregate", atacSeqAggregateInput) */

    // Find DNase-seq BAM matches
    /*
    val dnaseSeqFiles = dnaseAlignmentMatches()
    val dnaseMetadataPath = Files.createTempFile("dnase-metadata", ".tsv")
    writeDNaseMetadataFile(metadataPath, dnaseSeqFiles.values.toList())
    uploadFile(dnaseMetadataPath, "outputs/dnase-metadata.tsv")
    val dnaseSeqAggregateInput = motifTask.filter {
        dnaseSeqFiles.containsKey(it.occurrencesTsv.filename().split('.')[0])
    }.map {
        val bam = dnaseSeqFiles.get(it.occurrencesTsv.filename().split('.')[0])!!.bestDnaseBam
        val assembly = bam.assembly!!
        ATACAggregateInput(
            it.occurrencesTsv,
            HttpInputFile(bam.cloudMetadata!!.url, "${bam.accession}.bam", "dockerhub.wenglab.org/pratth/alpine:latest"),
            if (assembly == "GRCh38") "hg38" else "GRCh38", genomeMap.get(if (assembly == "GRCh38") "hg38" else "GRCh38"),
            true
        )
    }
    atacAggregateTask("dnaseseqaggregate", dnaseSeqAggregateInput)
    */

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
            HttpInputFile(it.cloudMetadata!!.url, "${it.accession}.bed.gz", "dockerhub.wenglab.org/pratth/alpine:latest")
        }
        MotifsInput(
            peaksBedGz = HttpInputFile(peaksFile.cloudMetadata!!.url, "${peaksFile.accession}.bed.gz", "dockerhub.wenglab.org/pratth/alpine:latest"),
            assemblyTwoBit = HttpInputFile(assemblyUrl(peaksFile.assembly!!), "${peaksFile.assembly}.2bit", "dockerhub.wenglab.org/pratth/alpine:latest"),
            chromSizes = HttpInputFile(chromeSizesUrl(peaksFile.assembly), "${peaksFile.assembly}.chrom.sizes", "dockerhub.wenglab.org/pratth/alpine:latest"),
            methylBeds = methylBeds,
	    assembly = peaksFile.assembly,
            rDHSs = if (peaksFile.assembly in rDHS_FILES)
                HttpInputFile(rDHS_FILES.getValue(peaksFile.assembly), "${peaksFile.assembly}-rDHSs.bed", "dockerhub.wenglab.org/pratth/alpine:latest")
            else null
        )
    }.toFlux()
    motifsTask("meme",motifsInputs)
}
