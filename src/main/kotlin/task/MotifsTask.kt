package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux


data class MotifsInput(
        val peaksBedGz: File,
        val assemblyTwoBit: File,
        val chromSizes: File,
        val methylBeds: List<File>? = null
)
data class MotifsOutput(val motifsJson: File, val occurrencesTsv: File)
data class MotifsParams(val methylPercentThreshold: Int? = null)

fun WorkflowBuilder.motifsTask(i: Publisher<MotifsInput>): Flux<MotifsOutput> = this.task("motifs", i) {
    val params = taskParams<MotifsParams>()
    val bedPrefix = input.peaksBedGz.filenameNoExt()

    dockerImage = "genomealmanac/factorbook-meme:v1.0.6"
    output = MotifsOutput(
            OutputFile("$bedPrefix.motifs.json", optional = true),
            OutputFile("$bedPrefix.occurrences.tsv", optional = true)
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
            $methylBedArgs \
            ${if(params.methylPercentThreshold != null) "--methyl-percent-threshold=${params.methylPercentThreshold}" else "" }
        """
}
