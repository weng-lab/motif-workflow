package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

data class TomTomInput(
    val queryMotif: File
)
data class TomTomOutput(
    val tomTomXml: File
)
data class TomTomParams(
    val threshold: Float? = 0.5F,
    val comparisonDatabases: List<File>
)

fun WorkflowBuilder.tomTomTask(i: Publisher<TomTomInput>): Flux<TomTomOutput> = this.task("tomtom", i) {
    val params = taskParams<TomTomParams>()
    val memePrefix = input.queryMotif.filenameNoExt()

    dockerImage = "gcr.io/devenv-215523/factorbook-meme:3bb1111c8a14707510d43b026005fa19c4905b27"
    output = TomTomOutput(
        OutputFile("$memePrefix.tomtom.xml")
    )
    command =
        """
        tomtom -thresh ${params.threshold} -oc $outputsDir ${input.queryMotif.dockerPath} \
            ${params.comparisonDatabases.joinToString(" ") { it.dockerPath }} && \
        cp $outputsDir/tomtom_out/tomtom.xml $outputsDir/$memePrefix.tomtom.xml
        """
}
