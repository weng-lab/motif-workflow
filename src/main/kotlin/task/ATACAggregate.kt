package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

data class ATACAggregateInput(
    val occurrences: File,
    val bam: File,
    val assembly: String,
    val genomeTar: File?,
    val dnase: Boolean = false
)

data class ATACAggregateOutput(
    val aggregateJson: File
)

data class ATACAggregateParams(
    val qValueThreshold: Float = 1E-6F,
    val extSize: Int = 1000
)

fun WorkflowBuilder.atacAggregateTask(name: String, i: Publisher<ATACAggregateInput>) = this.task<ATACAggregateInput, ATACAggregateOutput>(name, i) {
    
    val params = taskParams<ATACAggregateParams>()
    val assembly = if (input.assembly === "GRCh38") "hg38" else input.assembly
    val bedPrefix = input.occurrences.filenameNoExt().split(".")[0] + "-${input.bam.filenameNoExt().split(".")[0]}"

    dockerImage = "docker.io/genomealmanac/dnase-atac-signal-bias-correction:latest"
    output = ATACAggregateOutput(
        OutputFile("$bedPrefix.ATAC-aggregate.json", optional = true)
    )

    command =
        """
        cd / && cp ${input.bam.dockerPath} $outputsDir/input.bam && samtools index $outputsDir/input.bam && \
        mkdir -p $outputsDir/rgtdata/$assembly && \
        ${ if (input.genomeTar !== null) "tar xfvz ${input.genomeTar!!.dockerPath} --directory $outputsDir/rgtdata/$assembly &&" else "" } \
        RGTDATA=$outputsDir/rgtdata PYTHONPATH=/reg-gen /usr/bin/python3 -m app.main \
            --bed ${input.occurrences.dockerPath} \
            --bam $outputsDir/input.bam \
            --assembly $assembly \
            --ext-size ${params.extSize} \
            --occurrence-threshold ${params.qValueThreshold} \
            --aggregate \
            > $outputsDir/$bedPrefix.ATAC-aggregate.json
        """

}
