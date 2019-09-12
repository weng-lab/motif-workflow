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
    val experimentFiles = experimentFiles()

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
    }.toFlux()
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