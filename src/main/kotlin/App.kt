import krews.core.*
import krews.file.*
import krews.run
import reactor.core.publisher.*
import task.*
import util.*
import model.*
import java.nio.file.Files
import java.nio.file.Paths
import mu.KotlinLogging
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) = run(motifWorkflow, args)

data class MotifWorkflowParams(
    val methylMode: Boolean = false,
    val gencodeGtfs: Map<String, File>,
    val chromSizes: Map<String, File>,
    val conservationSignals: Map<String, File>,
    val dnasePileupDir: String
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
        runForChipSeq(params.gencodeGtfs, params.chromSizes, params.conservationSignals, params.dnasePileupDir)
    }
}

data class PeaksData(val experimentFile: EncodeFileWithExp, val assembly: String)
data class MotifBed(val peaksData: PeaksData, val bedFile: OutputFile)

fun WorkflowBuilder.runForChipSeq(gencodeGtfs: Map<String, File>, chromSizes: Map<String, File>, conservationSignals: Map<String, File>, dnasePileupDir: String) {
    val dnasePileupsPath = Paths.get(dnasePileupDir)
    if (!Files.isDirectory(dnasePileupsPath)) {
        throw Exception("Provided DNase pileup directory does not exist.")
    }
    val experimentFiles = chipSeqBedFiles()


    // Write peaks file & experiment accessions out to metadata file
    val metadataPath = Files.createTempFile("metadata", ".tsv")
    writeMetadataFile(metadataPath, experimentFiles)
    uploadFile(metadataPath, "outputs/metadata.tsv")

    // Set up motifs task for each peaks file
    val motifsInputs = experimentFiles.map { fileWithExp ->
        val (experimentFile, _) = fileWithExp
        MotifsInput(
            peaksBedGz = HttpInputFile(experimentFile.cloudMetadata!!.url, "${experimentFile.accession}.bed.gz"),
            assemblyTwoBit = HttpInputFile(assemblyUrl(experimentFile.assembly!!), "${experimentFile.assembly}.2bit"),
            chromSizes = HttpInputFile(chromSizesUrl(experimentFile.assembly), "${experimentFile.assembly}.chrom.sizes"),
            rDHSs = if (experimentFile.assembly in rDHS_FILES)
                HttpInputFile(rDHS_FILES.getValue(experimentFile.assembly), "${experimentFile.assembly}-rDHSs.bed")
            else null,
            meta = PeaksData(fileWithExp, experimentFile.assembly)
        )
    }.toFlux()
    val motifsOutputs = motifsTask("meme",motifsInputs)

    // perform TOMTOM on discovered motifs
    val tomTomInputs = motifsOutputs.map {
        TomTomInput(
            queryMotif = it.motifsXml
        )
    }
    tomTomTask("tomtom",tomTomInputs)

    // Aggregate
    val filterMotifsInputs = motifsOutputs.map { FilterMotifsInput(it.meta, it.motifsJson, it.occurrencesTsv) }
    val filteredMotifsOutputs = filterMotifsTask(filterMotifsInputs)
    val motifBeds = filteredMotifsOutputs.flatMap { motif -> motif.motifOccurencesBeds.files.flatMapMany { it.toFlux().map { MotifBed(motif.meta, it) } } }

    fun get_encode_file(file: EncodeFileWithExp, extension: String): File {
        val localPath = Paths.get("/data/projects/encode/data", file.experiment.accession, "${file.file.accession}$extension")
        return if (Files.exists(localPath)) {
            LocalInputFile(localPath.toString(), "${file.file.accession}$extension")
        } else {
            HttpInputFile(file.file.cloudMetadata!!.url, "${file.file.accession}$extension")
        }
    }
    fun get_chrom_sizes(assembly: String): File = chromSizes.getOrElse(assembly) { HttpInputFile(chromSizesUrl(assembly), chromSizesName(assembly)) }

    // Conservation
    val conservationMotifs = motifBeds.filter { it.peaksData.assembly in conservationSignals }
    val conservationMetadata = conservationMotifs.onErrorResume { Mono.empty() }.collectList().map {
        val conservationMetadataPath = Files.createTempFile("conservation_metadata", ".tsv")
        writeConservationMetadataFile(conservationMetadataPath, it)
        uploadFile(conservationMetadataPath, "outputs/conservation_metadata.tsv")
        Unit
    }


    val conservationSplitInputs = conservationMotifs
        .map { motifBed ->
            val peaksInputFile = motifBed.bedFile
            val gencodeGTF = gencodeGtfs[motifBed.peaksData.assembly] ?: throw IllegalArgumentException("No GTF file passed for ${motifBed.peaksData.assembly}")
            val sizes = get_chrom_sizes(motifBed.peaksData.assembly)

            SplitPeaksInput(peaksInputFile, gencodeGTF, sizes, motifBed.peaksData.assembly, Unit)
        }.toFlux()

    val conservationSplitOuputs = splitPeaksTask("conservation", conservationSplitInputs)

    val conservationSplitAggregateInputs = conservationSplitOuputs.map { splitOut ->
        val signalBigWig = conservationSignals[splitOut.assembly]!!
        val sizes = get_chrom_sizes(splitOut.assembly)
        AggregateInput(
            allPeaksBed = splitOut.allPeaks,
            proximalPeaksBed = splitOut.proximalPeaks,
            distalPeaksBed = splitOut.distalPeaks,
            signalBigWig = signalBigWig,
            chromSizes = sizes
        )
    }.toFlux()

    aggregateTask("conservation", conservationSplitAggregateInputs) {
        "conservation"
    }
 
    // DNase
    val dnaseAlignmentFiles = dnaseAlignmentFiles()
    val dnaseAlignmentByMatchCriteria: Map<ExperimentMatchCriteria, EncodeFileWithExp> = dnaseAlignmentFiles
        .map { fileWithExp -> fileWithExp.toMatchCriteria() to fileWithExp }
        .filter { it.first != null }
        .groupBy { it.first!! }
        .mapValues { entry -> 
            val candidateExperiments = entry.value.toMutableList()
            // TODO: pick by QC
            candidateExperiments[0].second
        }
    val dnaseMotifs = motifBeds
        .map {
            val match = it.peaksData.experimentFile.toMatchCriteria()!!
            if (match in dnaseAlignmentByMatchCriteria) {
                Triple(it, dnaseAlignmentByMatchCriteria[match]!!, match)
            } else {
                null
            }
        }
        .filter { it != null }
        .map { it!! }

    val dnaseMetadata = dnaseMotifs.onErrorResume { Mono.empty() }.collectList().map {
        val dnaseMetadataPath = Files.createTempFile("dnase_metadata", ".tsv")
        writeDnaseMetadataFile(dnaseMetadataPath, it)
        uploadFile(dnaseMetadataPath, "outputs/dnase_metadata.tsv")
        Unit
    }

    val dnaseSplitInputs = dnaseMotifs
        .map {
            val alignmentFileAccession = it.second.file.accession
            val pileupPath = dnasePileupsPath.resolve("${alignmentFileAccession}.bigWig")
            if (!Files.exists(pileupPath)) {
                log.error { "Missing DNase pileup for ${alignmentFileAccession}" }
                null
            } else {
                Pair(it.first, LocalInputFile(localPath = pileupPath.toAbsolutePath().toString()))
            }
        }
        .filter { it != null}
        .map { it!! }
        .map {
            val assembly = it.first.peaksData.assembly
            val gencodeGTF = gencodeGtfs[assembly] ?: throw IllegalArgumentException("No GTF file passed for ${assembly}")
            val sizes = get_chrom_sizes(assembly)

            SplitPeaksInput<File>(it.first.bedFile, gencodeGTF, sizes, assembly, it.second)
        }
    
    val dnaseSplitOuputs = splitPeaksTask("dnase", dnaseSplitInputs)

    val dnaseSplitAggregateInputs = dnaseSplitOuputs.map { splitOut ->
        val sizes = get_chrom_sizes(splitOut.assembly)
        AggregateInput(
            splitOut.allPeaks,
            splitOut.proximalPeaks,
            splitOut.distalPeaks,
            splitOut.meta,
            sizes
        )
    }.toFlux()
    aggregateTask("dnase", dnaseSplitAggregateInputs) { file ->
        file.filenameNoExt()
    }

    // Histone
    val histoneSignalFiles = histoneSignalFiles()
    val histoneSignalsByMatchCriteria: Map<ExperimentMatchCriteria, Set<EncodeFileWithExp>> = histoneSignalFiles
        .map { fileWithExp -> fileWithExp.toMatchCriteria() to fileWithExp }
        .filter { it.first != null }
        .groupBy { it.first!! }
        .mapValues { entry -> entry.value.map { it.second }.toSet() }
    val histoneMotifs = motifBeds
        .map {
            val match = it.peaksData.experimentFile.toMatchCriteria()!!
            if (match in histoneSignalsByMatchCriteria) {
                Triple(it, histoneSignalsByMatchCriteria[match]!!, match)
            } else {
                null
            }
        }
        .filter { it != null }
        .map { it!! }

    val histoneMetadata = histoneMotifs.onErrorResume { Mono.empty() }.collectList().map {
        val dnaseMetadataPath = Files.createTempFile("histone_metadata", ".tsv")
        writeHistoneMetadataFile(dnaseMetadataPath, it)
        uploadFile(dnaseMetadataPath, "outputs/histone_metadata.tsv")
        Unit
    }

    val histoneSplitInputs = histoneMotifs
        .map {
            val assembly = it.first.peaksData.assembly
            val gencodeGTF = gencodeGtfs[assembly] ?: throw IllegalArgumentException("No GTF file passed for ${assembly}")
            val sizes = get_chrom_sizes(assembly)

            SplitPeaksInput<Set<EncodeFileWithExp>>(it.first.bedFile, gencodeGTF, sizes, assembly, it.second)
        }

    val histoneSplitOuputs = splitPeaksTask("histone", histoneSplitInputs)

    val histoneSplitAggregateInputs = histoneSplitOuputs.flatMap { splitOut ->
        splitOut.meta.map { signal ->
            val signalBigWig = get_encode_file(signal, ".bigWig")
            val sizes = get_chrom_sizes(signal.file.assembly!!)
            AggregateInput(
                splitOut.allPeaks,
                splitOut.proximalPeaks,
                splitOut.distalPeaks,
                signalBigWig,
                sizes
            )
        }.toFlux()
    }.toFlux()
    aggregateTask("histone", histoneSplitAggregateInputs) { file ->
        file.filenameNoExt()
    }

    thread(start = true, name = "metadata-watcher") {
        conservationMetadata.block()
        dnaseMetadata.block()
        histoneMetadata.block()

    }
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
            chromSizes = HttpInputFile(chromSizesUrl(peaksFile.assembly), "${peaksFile.assembly}.chrom.sizes"),
            methylBeds = methylBeds,
            rDHSs = if (peaksFile.assembly in rDHS_FILES)
                HttpInputFile(rDHS_FILES.getValue(peaksFile.assembly), "${peaksFile.assembly}-rDHSs.bed")
            else null,
            meta = PeaksData(methylBedMatch.chipSeqFile, peaksFile.assembly)
        )
    }.toFlux()
    motifsTask("meme",motifsInputs)
}