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

    dockerImage = "gcr.io/devenv-215523/factorbook-meme:e4c1372ac32f3d16163d5a8fe15402f4fe1c6d42"
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
