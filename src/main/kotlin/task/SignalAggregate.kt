package task

import krews.core.*
import krews.file.*
import model.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

data class AggregateInput(
    val bedFile: File,
    val signalFile: SignalFile,
    val name: String
)

data class AggregateOutput(
    val aggregateJson: File,
    val name: String
)

data class AggregateParams(
    val extSize: Int?,
    val startIndex: Int?,
    val endIndex: Int?,
    val resolution: Int?,
    val decimalResolution: Int?,
    val j: Int?
)

fun WorkflowBuilder.aggregateTask(name: String, i: Publisher<AggregateInput>) = this.task<AggregateInput, AggregateOutput>(name, i) {
    
    val taskParams = taskParams<AggregateParams>()

    dockerImage = "genomealmanac/signal-extraction-task:latest" // "docker.pkg.github.com/krews-community/signal-extraction-task/signal-extraction-task:0.1.1"
    output = AggregateOutput(OutputFile("${input.name}.${input.signalFile.name}.signal.json"), input.name)

    command =
        """
        cd / && awk '{ print $2 "\t" $3 "\t" $4 "\t" $1 "\t" $5 "\t" $6 }' ${input.bedFile.dockerPath} | grep -v chromosome > $outputsDir/${input.name}.temp && \
        PYTHONNOUSERSITE=1 python3 -m app matrix --bed-file $outputsDir/${input.name}.temp \
            --signal-file ${input.signalFile.file.dockerPath} \
            ${ if (taskParams.extSize !== null) "--extsize ${taskParams.extSize}" else "" } \
            ${ if (taskParams.startIndex !== null) "--start-index ${taskParams.startIndex}" else "" } \
            ${ if (taskParams.endIndex !== null) "--end-index ${taskParams.endIndex}" else "" } \
            ${ if (taskParams.resolution !== null) "--resolution ${taskParams.resolution}" else "" } \
            ${ if (taskParams.decimalResolution !== null) "--decimal-resolution ${taskParams.decimalResolution}" else "" } \
            ${ if (taskParams.j !== null) "-j ${taskParams.j}" else "" } \
            --grouped \
            --output-file $outputsDir/${input.name}.${input.signalFile.name}.signal.json && \
        rm $outputsDir/${input.name}.temp
        """

}
