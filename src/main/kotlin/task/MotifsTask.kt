package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

data class MotifsInput<Meta>(
    val peaksBedGz: File,
    val assemblyTwoBit: File,
    val chromSizes: File,
    val methylBeds: List<File>? = null,
    val rDHSs: File? = null,
    val meta: Meta
)
data class MotifsOutput<Meta>(
    val motifsJson: File,
    val motifsXml: File,
    val occurrencesTsv: File,
    val rDHSOccurrencesTsv: File?,
    val meta: Meta
)
data class MotifsParams(val methylPercentThreshold: Int? = null)

fun <Meta> WorkflowBuilder.motifsTask(name: String,i: Publisher<MotifsInput<Meta>>): Flux<MotifsOutput<Meta>> = this.task(name, i) {
    val params = taskParams<MotifsParams>()
    val bedPrefix = input.peaksBedGz.filenameNoExt()

    dockerImage = "gcr.io/devenv-215523/factorbook-meme:3bb1111c8a14707510d43b026005fa19c4905b27"
    output = MotifsOutput(
        OutputFile("$bedPrefix.motifs.json", optional = true),
        OutputFile("$bedPrefix.meme.xml", optional = true),
        OutputFile("$bedPrefix.occurrences.tsv", optional = true),
        if (input.rDHSs != null) OutputFile("$bedPrefix.extra.fimo/$bedPrefix.${input.rDHSs!!.path}.occurrences.tsv") else null,
        input.meta
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