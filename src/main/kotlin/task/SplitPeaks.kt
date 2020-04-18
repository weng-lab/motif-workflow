package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import util.*


data class SplitPeaksParams(
    val proximalSize: Int = 2000
)

data class SplitPeaksInput<T>(
    val peaksBed: File,
    val gencodeGTF: File,
    val chromSizes: File,
    val assembly: String,
    val meta: T
)
data class SplitPeaksOutput<T>(val assembly: String, val meta: T, val allPeaks: File, val proximalPeaks: File, val distalPeaks: File)

fun <T> WorkflowBuilder.splitPeaksTask(type: String, i: Publisher<SplitPeaksInput<T>>): Flux<SplitPeaksOutput<T>> = this.task("split-peaks-${type}", i) {
    val params = taskParams<SplitPeaksParams>()
    val bedPrefix = input.peaksBed.filenameNoExt()

    val unzippedBed = OutputFile("${bedPrefix}.bed")
    val proximalPeaks = OutputFile("${bedPrefix}p.bed")
    val distalPeaks = OutputFile("${bedPrefix}d.bed")

    dockerImage = "biocontainers/bedtools:v2.28.0_cv2"
    output = SplitPeaksOutput(input.assembly, input.meta, input.peaksBed, proximalPeaks, distalPeaks)
    command =
        """
        set -e
        cat ${input.gencodeGTF.dockerPath} \
            | grep -v "#" \
            | awk -v OFS="\t" '${"$"}3=="gene" { if (${"$"}7 == "+") { start=${"4"} } else { start=${"$"}5-1 } print ${"$"}1,start,start+1,${"$"}10, ".", ${"$"}7}' \
            | sort -k1,1 -k2,2n \
            > tss.bed
        cat ${input.peaksBed.dockerPath} | sort -k1,1 -k2,2n > ${unzippedBed.dockerPath}
        cat ${unzippedBed.dockerPath} \
            | bedtools closest -a - -b tss.bed -d \
            | awk -v OFS="\t" '{if ($2+$10 <= $12) {dis=($12 - ($2+$10))} else {dis=(($2+$10) - $13)}; if (dis > ${params.proximalSize}) {next}; $6=$16; print $0;}' \
            | cut -f1-10 \
            > ${proximalPeaks.dockerPath}
        cat ${unzippedBed.dockerPath} \
            | bedtools closest -a - -b tss.bed -d \
            | awk -v OFS="\t" '{if ($2+$10 <= $12) {dis=($12 - ($2+$10))} else {dis=(($2+$10) - $13)}; if (dis <= ${params.proximalSize}) {next}; print $0;}' \
            | cut -f1-10 \
            > ${distalPeaks.dockerPath}
        """
}
