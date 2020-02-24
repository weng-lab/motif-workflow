package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

data class MotifsInput(
    val peaksBedGz: File,
    val assemblyTwoBit: File,
    val chromSizes: File,
    val methylBeds: List<File>? = null,
    val rDHSs: File? = null
)
data class MotifsOutput(val motifsJson: File, val occurrencesTsv: File, val rDHSOccurrencesTsv: File?)
data class MotifsParams(val methylPercentThreshold: Int? = null)

fun WorkflowBuilder.motifsTask(i: Publisher<MotifsInput>): Flux<MotifsOutput> = this.task("motifs", i) {
    val params = taskParams<MotifsParams>()
    val bedPrefix = input.peaksBedGz.filenameNoExt()

    dockerImage = "docker.pkg.github.com/krews-community/factorbook-meme/factorbook-meme:latest"
    output = MotifsOutput(
        OutputFile("$bedPrefix.motifs.json", optional = true),
        OutputFile("$bedPrefix.occurrences.tsv", optional = true),
        if (input.rDHSs != null) OutputFile("$bedPrefix.extra.fimo/$bedPrefix.${input.rDHSs!!.path}.occurrences.tsv") else null
    )
    val methylBedArgs = if (input.methylBeds != null) {
        input.methylBeds!!.joinToString(" \\\n") { "--methyl-beds=${it.dockerPath}" }
    } else ""
    command =
        """
        java -jar /app/meme.jar --peaks=${input.peaksBedGz.dockerPath} \
            --twobit=${input.assemblyTwoBit.dockerPath} \
            --chrom-info=${input.chromSizes.dockerPath} \
            --output-dir=$outputsDir \
            --chrom-filter=chrEBV \
            ${if (input.rDHSs != null) "--extra-fimo-regions=${input.rDHSs!!.dockerPath}" else ""} \
            $methylBedArgs \
            ${if(params.methylPercentThreshold != null) "--methyl-percent-threshold=${params.methylPercentThreshold}" else ""}
        """
}
