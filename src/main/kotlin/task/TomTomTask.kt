package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

data class TomTomInput(
    val queryMotif: File
)
data class TomTomOutput(
    val tomTomXml: File,
    val tomTomTsv: File
)
data class TomTomParams(
    val threshold: Float? = 0.5F,
    val comparisonDatabases: List<File>
)

fun WorkflowBuilder.tomTomTask(name: String,i: Publisher<TomTomInput>) = this.task<TomTomInput,TomTomOutput>(name, i) {
    val params = taskParams<TomTomParams>()
    val memePrefix = input.queryMotif.filenameNoExt()

    dockerImage = "gcr.io/devenv-215523/factorbook-meme:3bb1111c8a14707510d43b026005fa19c4905b27"
    output = TomTomOutput(
        tomTomXml = OutputFile("${memePrefix}.tomtom.xml"),
        tomTomTsv =  OutputFile("${memePrefix}.tomtom.tsv")
    )
    command =
        """
        tomtom -thresh ${params.threshold}  -oc $outputsDir  ${input.queryMotif.dockerPath} \
            ${params.comparisonDatabases.joinToString(" ") { it.dockerPath }} && \
        cp $outputsDir/tomtom.xml $outputsDir/$memePrefix.tomtom.xml && \
        cp $outputsDir/tomtom.tsv $outputsDir/$memePrefix.tomtom.tsv
        """
}
