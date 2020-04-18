package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux


data class AggregateInput(
    val allPeaksBed: File,
    val proximalPeaksBed: File,
    val distalPeaksBed: File,
    val signalBigWig: File,
    val chromSizes: File
)
data class AggregateOutput(val aggregateTsv: File, val proximalAggregateTsv: File, val distalAggregateTsv: File)

fun WorkflowBuilder.aggregateTask(type: String, i: Publisher<AggregateInput>, signalName: (signalBigWig: File) -> String): Flux<AggregateOutput> = this.task("aggregate-${type}", i) {
    val bedPrefix = input.allPeaksBed.filenameNoExt()
    val signalPrefix = input.signalBigWig.filenameNoExt()
    val finalSignalPrefix = signalName(input.signalBigWig)
    
    val intermediateFile = OutputFile("${bedPrefix}_${signalPrefix}.aggregate.tsv")
    val outputFile = OutputFile("${bedPrefix}_${finalSignalPrefix}.aggregate.tsv")
    
    val intermediateFileProximal = OutputFile("${bedPrefix}p_${signalPrefix}.aggregate.tsv")
    val outputFileProximal = OutputFile("${bedPrefix}_${finalSignalPrefix}.proximal.aggregate.tsv", optional = true)

    val intermediateFileDistal = OutputFile("${bedPrefix}d_${signalPrefix}.aggregate.tsv")
    val outputFileDistal = OutputFile("${bedPrefix}_${finalSignalPrefix}.distal.aggregate.tsv", optional = true)

    dockerImage = "gcr.io/devenv-215523/factorbook-aggregate:v1.0.6"
    output = AggregateOutput(outputFile, outputFileProximal, outputFileDistal)
    command =
        """
        java -jar /app/aggregate.jar \
            --peaks=${input.allPeaksBed.dockerPath} \
            --peaks=${input.proximalPeaksBed.dockerPath} \
            --peaks=${input.distalPeaksBed.dockerPath} \
            --signal=${input.signalBigWig.dockerPath} \
            --output-dir=$outputsDir \
            --chrom-filter=chrEBV \
            --chrom-info=${input.chromSizes.dockerPath} \
            --align-strand
        if [ "${intermediateFile.dockerPath}" != "${outputFile.dockerPath}" ]; then
            mv ${intermediateFile.dockerPath} ${outputFile.dockerPath}
        fi
        mv ${intermediateFileProximal.dockerPath} ${outputFileProximal.dockerPath}
        mv ${intermediateFileDistal.dockerPath} ${outputFileDistal.dockerPath}
        """
}
