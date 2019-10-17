package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux


data class MotifsInput(
        val peaksBedGz: File,
        val assemblyTwoBit: File,
        val chromSizes: File,
        val methylBed: File? = null
)
data class MotifsOutput(val motifsJson: File, val occurrencesTsv: File)
data class MotifsParams(val methylPercentThreshold: Int? = null)

fun WorkflowBuilder.motifsTask(i: Publisher<MotifsInput>): Flux<MotifsOutput> = this.task("motifs", i) {
    val params = taskParams<MotifsParams>()
    val bedPrefix = input.peaksBedGz.filenameNoExt()
    val unzippedBedPath = input.peaksBedGz.dockerPath.dropLast(3)

    dockerImage = "genomealmanac/factorbook-meme:v1.0.3"
    output = MotifsOutput(OutputFile("$bedPrefix.motifs.json"), OutputFile("$bedPrefix.occurrences.tsv"))
    command =
        """
        gunzip ${input.peaksBedGz.dockerPath}
        java -jar /app/meme.jar --peaks=$unzippedBedPath \
            --twobit=${input.assemblyTwoBit.dockerPath} \
            --chrom-info=${input.chromSizes.dockerPath} \
            --output-dir=$outputsDir \
            --chrom-filter=chrEBV \
            ${if(input.methylBed != null) "--methyl-bed=${input.methylBed!!.dockerPath}" else "" } \
            ${if(params.methylPercentThreshold != null) "--methyl-percent-threshold=${params.methylPercentThreshold}" else "" }
        """
}
