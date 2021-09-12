package util

import java.nio.file.*
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

fun writeMetadataFile(metadataPath: Path, experimentFiles: List<EncodeFileWithExp>) {
    Files.newBufferedWriter(metadataPath).use { writer ->
        writer.write("#peaks_accession\tdataset_accession\tassembly\ttarget\tbiosample\n")
        //writer.write("#peaks_accession\tdataset_accession\tassembly\n")
        for((experimentFile, experiment) in experimentFiles) {            
            val target = experiment.target?.label
            val biosample = experiment.biosampleOntology.termName
            writer.write("${experimentFile.accession}\t${experiment.accession}\t${experimentFile.assembly}\t${target}\t${biosample}\n")
            //writer.write("${experimentFile.accession}\t${experiment.accession}\t${experimentFile.assembly}\n")
        }
    }
}

fun writeMethylMetadataFile(metadataPath: Path, methylFileMatches: List<MethylFileMatch>) {
    Files.newBufferedWriter(metadataPath).use { writer ->
        val header = "#peaks_accession\tdataset_accession\tmethyl_dataset_accession\tmethyl_bed_accessions\t" +
                "assembly\tbiosample_ontology_id\tdonor\tlife_stage\tage\tage_units\n"
        writer.write(header)
        for((chipSeqFile, methylExperiment, methylBedFiles, matchCriteria) in methylFileMatches) {
            val methylBedAccessions = methylBedFiles.joinToString(",") { it.accession!! }
            val line = "${chipSeqFile.file.accession}\t${chipSeqFile.experiment.accession}\t${methylExperiment.accession}\t" +
                    "$methylBedAccessions\t${matchCriteria.assembly}\t${matchCriteria.bioSampleOntologyId}\t${matchCriteria.donorId}\t" +
                    "${matchCriteria.lifeStage}\t${matchCriteria.age}\t${matchCriteria.ageUnits}\n"
            writer.write(line)
        }
    }
}

fun writeATACMetadataFile(metadataPath: Path, atacFileMatches: List<ATACMatch>) {
    Files.newBufferedWriter(metadataPath).use { writer ->
        val header = "#peaks_accession\tdataset_accession\tatac_dataset_accession\tatac_bam_accession\t" +
                "assembly\tbiosample_ontology_id\tdonor\tlife_stage\tage\tage_units\n"
        writer.write(header)
        for((chipSeqFile, atacExperiment, atacBamFiles, matchCriteria) in atacFileMatches) {
            val atacBamAccessions = atacBamFiles.joinToString(",") { it.accession!! }
            val line = "${chipSeqFile.file.accession}\t${chipSeqFile.experiment.accession}\t" +
                    "$atacBamAccessions\t${atacExperiment.accession}\t${matchCriteria.assembly}\t${matchCriteria.bioSampleOntologyId}\n"
            writer.write(line)
        }
    }
}

fun writeDNaseMetadataFile(metadataPath: Path, dnaseFileMatches: List<DNaseMatch>) {
    Files.newBufferedWriter(metadataPath).use { writer ->
        val header = "#peaks_accession\tdataset_accession\tdnase_dataset_accession\tdnase_bam_accession\t" +
                "assembly\tbiosample_ontology_id\tdonor\tlife_stage\tage\tage_units\n"
        writer.write(header)
        for((chipSeqFile, dnaseExperiment, dnaseBamFile, matchCriteria) in dnaseFileMatches) {
            val line = "${chipSeqFile.file.accession}\t${chipSeqFile.experiment.accession}\t${dnaseExperiment.accession}\t" +
                    "${dnaseBamFile.accession}\t${matchCriteria.assembly}\t${matchCriteria.bioSampleOntologyId}\t${matchCriteria.donorId}\t" +
                    "${matchCriteria.lifeStage}\t${matchCriteria.age}\t${matchCriteria.ageUnits}\n"
            writer.write(line)
        }
    }
}
