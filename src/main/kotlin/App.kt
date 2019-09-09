import krews.core.*
import krews.file.*
import krews.run
import model.*
import mu.KotlinLogging
import reactor.core.publisher.*
import task.*
import util.*

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) = run(motifWorkflow, args)

val motifWorkflow = workflow("motif-workflow") {
    //val params = params<SampleParams>()

    // TODO: Remove all sublists
    val searchResult = requestEncodeSearch()
    val experimentAccessions = searchResult.graph.map { it.accession }
            .subList(0, 20)

    val motifsInputs = runParallel("Experiment Lookup", experimentAccessions, 50) { experimentAccession ->
        val experiment = requestEncodeExperiment(experimentAccession)
        val filteredExperiments = experiment.files
                .filter { it.isReleased() && it.isReplicatedPeaks() }
        filteredExperiments.map {
            MotifsInput(
                    peaksBedGz = HttpInputFile(it.cloudMetadata!!.url, "${it.accession}.bed.gz"),
                    assemblyTwoBit = HttpInputFile(assemblyUrl(it.assembly!!), "${it.assembly}.2bit"),
                    chromSizes = HttpInputFile(chromeSizesUrl(it.assembly), "${it.assembly}.chrom.sizes")
            )
        }
    }.flatten()

    motifsTask(motifsInputs.subList(0, 1).toFlux())
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

