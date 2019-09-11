package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux


data class MotifsInput(val peaksBedGz: File, val assemblyTwoBit: File, val chromSizes: File)
data class MotifsOutput(val motifsJson: File, val occurrencesTsv: File)

fun WorkflowBuilder.motifsTask(i: Publisher<MotifsInput>): Flux<MotifsOutput> = this.task("motifs", i) {
    val bedPrefix = input.peaksBedGz.filenameNoExt()
    val unzippedBedPath = input.peaksBedGz.dockerPath.dropLast(3)

    dockerImage = "genomealmanac/factorbook-meme:v1.0.0"
    output = MotifsOutput(OutputFile("$bedPrefix.motifs.json"), OutputFile("$bedPrefix.occurrences.tsv"))
    command =
        """
        gunzip ${input.peaksBedGz.dockerPath}
        java -jar /app/meme.jar --peaks=$unzippedBedPath \
            --twobit=${input.assemblyTwoBit.dockerPath} \
            --chrom-info=${input.chromSizes.dockerPath} \
            --output-dir=$outputsDir
        """
}
