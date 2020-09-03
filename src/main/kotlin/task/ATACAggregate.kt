package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

data class ATACAggregateInput(
    val occurrences: File,
    val bam: File,
    val assembly: String,
    val genomeTar: File?
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
    val bedPrefix = input.occurrences.filenameNoExt()

    dockerImage = "gcr.io/devenv-215523/dnase-atac-signal-bias-correction:98448d20d48561549add9a13d5161c4e1f366ae5"
    output = ATACAggregateOutput(
        OutputFile("$bedPrefix.ATAC-aggregate.json", optional = true)
    )

    command =
        """
        ${ if (input.genomeTar !== null) "tar xfvz ${input.genomeTar!!.dockerPath} --directory /root/rgtdata/${input.assembly} &&" else "" } \
        python3 -m app.main \
            --bed ${input.occurrences.dockerPath} \
            --bam ${input.bam.dockerPath} \
            --assembly ${input.assembly} \
            --ext-size ${params.extSize} \
            --occurrence-threshold ${params.qValueThreshold} \
            --aggregate \
            > $outputsDir/$bedPrefix.ATAC-aggregate.json
        """

}
