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

    dockerImage = "genomealmanac/factorbook-meme:b00c389a58c7400fc94709fed0f1234f100aecdd"
    output = TomTomOutput(
        tomTomXml =  OutputFile("${memePrefix}.tomtom.xml"),        
        tomTomTsv =  OutputFile("${memePrefix}.tomtom.tsv")
    )
    command =
        """
        java -jar /app/meme.jar --run-tomtom \
            ${params.comparisonDatabases.joinToString(" ") { "--tomtom-comparison-databases=" + it.dockerPath }} \
            --output-dir=$outputsDir \
            --tomtom-threshold=${params.threshold} \
            --meme-xml=${input.queryMotif.dockerPath}             
        """    
}
