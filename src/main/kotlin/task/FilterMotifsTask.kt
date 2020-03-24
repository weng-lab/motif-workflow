package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import util.*

data class FilterMotifsInput<Meta>(
    val meta: Meta,
    val motifsJson: File,
    val motifOccurencesTsv: File
)
data class FilterMotifsOutput<Meta>(val meta: Meta, val motifOccurencesBeds: OutputDirectory)

fun <Meta> WorkflowBuilder.filterMotifsTask(i: Publisher<FilterMotifsInput<Meta>>): Flux<FilterMotifsOutput<Meta>> = this.task("filter-motifs", i) {
    val prefix = input.motifsJson.filenameNoExt()
    val bedDir = OutputDirectory("${prefix}")

    dockerImage = "gcr.io/devenv-215523/motif-workflow-filter-motifs:v1.0.0"
    output = FilterMotifsOutput(input.meta, bedDir)
    command =
        """
        set -e
        mkdir -p ${bedDir.dockerPath}
        cat ${input.motifsJson.dockerPath} | jq --raw-output '.motifs | map(select(.flank_control_data.z_score > 0 and .flank_control_data.p_value < 0.05 and .shuffled_control_data.z_score > 0 and .shuffled_control_data.p_value < 0.05)) | .[].name' > motifs.tsv

        while read motif
        do
            cat ${input.motifOccurencesTsv.dockerPath} \
            | awk -v motif=${'$'}motif '{${'$'}1 == motif}' \
            | awk 'OFS="\t" { print $2,$3,$4,".",0,$5,0,0,$6,int(($3-$2)/2)+$2 }' \
            > ${bedDir.dockerPath}/${'$'}motif.bed
        done < <(cat motifs.tsv)
        """
}
