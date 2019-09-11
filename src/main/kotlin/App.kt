import krews.core.*
import krews.file.*
import krews.run
import model.*
import mu.KotlinLogging
import reactor.core.publisher.*
import task.*
import util.*
import java.nio.file.Files

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) = run(motifWorkflow, args)

val motifWorkflow = workflow("motif-workflow") {
    val searchResult = requestEncodeSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }
            .subList(0, 20) // TODO: Remove this sublist

    val experimentFiles =
            runParallel("Experiment Lookup", experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        val filteredExperiments = experiment.files
                .filter { it.isReleased() && it.isReplicatedPeaks() }
        filteredExperiments.map { experiment to it }
    }.flatten()

    // Write peaks file & experiment accessions out to metadata file
    val metadataPath = Files.createTempFile("metadata", ".tsv")
    Files.newBufferedWriter(metadataPath).use { writer ->
        writer.write("#peaks_accession\tdataset_accession\tassembly\n")
        for((experiment, experimentFile) in experimentFiles) {
            writer.write("${experimentFile.accession}\t${experiment.accession}\t${experimentFile.assembly}\n")
        }
    }
    uploadFile(metadataPath, "outputs/metadata.tsv")

    // Set up motifs task for each peaks file
    val motifsInputs = experimentFiles.map { (_, experimentFile) ->
        MotifsInput(
                peaksBedGz = HttpInputFile(experimentFile.cloudMetadata!!.url, "${experimentFile.accession}.bed.gz"),
                assemblyTwoBit = HttpInputFile(assemblyUrl(experimentFile.assembly!!), "${experimentFile.assembly}.2bit"),
                chromSizes = HttpInputFile(chromeSizesUrl(experimentFile.assembly), "${experimentFile.assembly}.chrom.sizes")
        )
    }.subList(0, 1).toFlux() // TODO: Remove this sublist
    motifsTask(motifsInputs)
}

val URL_ASSEMBLY_REPLACEMENTS = mapOf("GRCh38" to "hg38")

private fun assemblyUrl(assembly: String): String {
    val urlAssembly = URL_ASSEMBLY_REPLACEMENTS.getOrDefault(assembly, assembly)
    return "https://hgdownload-test.gi.ucsc.edu/goldenPath/$urlAssembly/bigZips/$urlAssembly.2bit"
}
private fun chromeSizesUrl(assembly: String): String {
    val urlAssembly = URL_ASSEMBLY_REPLACEMENTS.getOrDefault(assembly, assembly)
    return "https://hgdownload-test.gi.ucsc.edu/goldenPath/$urlAssembly/bigZips/$urlAssembly.chrom.sizes"
}
