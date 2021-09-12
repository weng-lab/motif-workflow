package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

data class MotifsInput(
    val peaksBedGz: File,
    val assemblyTwoBit: File,
    val chromSizes: File,
    val assembly: String,
    val methylBeds: List<File>? = null,
    val rDHSs: File? = null
)
data class MotifsOutput(
    val motifsJson: File,
    val motifsXml: File,
    val occurrencesTsv: File,
    val assembly: String,
    val rDHSOccurrencesTsv: File?
)
data class MotifsParams(val methylPercentThreshold: Int? = null)

fun WorkflowBuilder.motifsTask(name: String,i: Publisher<MotifsInput>) = this.task<MotifsInput,MotifsOutput>(name, i) {
    val params = taskParams<MotifsParams>()
    val bedPrefix = input.peaksBedGz.filenameNoExt()

    dockerImage = "ghcr.io/krews-community/factorbook-meme/factorbook-meme:latest"
    output = MotifsOutput(
        OutputFile("$bedPrefix.motifs.json", optional = true),
        OutputFile("$bedPrefix.meme.xml", optional = true),
        OutputFile("$bedPrefix.occurrences.tsv", optional = true),
	input.assembly,
        if (input.rDHSs != null) OutputFile("$bedPrefix.extra.fimo/$bedPrefix.${input.rDHSs!!.path}.occurrences.tsv") else null
    )
    val methylBedArgs = if (input.methylBeds != null) {
        input.methylBeds!!.joinToString(" \\\n") { "--methyl-beds=${it.dockerPath}" }
    } else ""
    command =
        """
        java -jar /app/meme.jar --peaks=${input.peaksBedGz.dockerPath} \
            --twobit=${input.assemblyTwoBit.dockerPath} \
            --chrom-info=${input.chromSizes.dockerPath} \
            --output-dir=$outputsDir \
            --chrom-filter=chrEBV \
            --chrom-inclusion=chr1 --chrom-inclusion=chr2 --chrom-inclusion=chr3 --chrom-inclusion=chr4 --chrom-inclusion=chr5 --chrom-inclusion=chr6 --chrom-inclusion=chr7 --chrom-inclusion=chr8 \
            --chrom-inclusion=chr9 --chrom-inclusion=chr10 --chrom-inclusion=chr11 --chrom-inclusion=chr12 --chrom-inclusion=chr13 --chrom-inclusion=chr14 --chrom-inclusion=chr15 --chrom-inclusion=chr16 \
            --chrom-inclusion=chr17 --chrom-inclusion=chr18 --chrom-inclusion=chr19 --chrom-inclusion=chr20 --chrom-inclusion=chr21 --chrom-inclusion=chr22 --chrom-inclusion=chrX --chrom-inclusion=chrY \
            ${if (input.rDHSs != null) "--extra-fimo-regions=${input.rDHSs!!.dockerPath}" else ""} \
            $methylBedArgs \
            ${if(params.methylPercentThreshold != null) "--methyl-percent-threshold=${params.methylPercentThreshold}" else ""}
        """
}
