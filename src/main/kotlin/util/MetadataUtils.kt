package util

import java.nio.file.*

fun writeMetadataFile(metadataPath: Path, experimentFiles: List<EncodeFileWithExp>) {
    Files.newBufferedWriter(metadataPath).use { writer ->
        writer.write("#peaks_accession\tdataset_accession\tassembly\n")
        for((experimentFile, experiment) in experimentFiles) {
            writer.write("${experimentFile.accession}\t${experiment.accession}\t${experimentFile.assembly}\n")
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