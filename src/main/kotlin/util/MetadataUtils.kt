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
        val header = "#peaks_accession\tdataset_accession\tmethyl_bed_accession\t" +
                "methyl_dataset_accession\tassembly\tbiosample_ontology_id\tdonor\tlife_stage\tage\tage_units\n"
        writer.write(header)
        for((chipSeqFile, methylBedFile, matchCriteria) in methylFileMatches) {
            val line = "${chipSeqFile.file.accession}\t${chipSeqFile.experiment.accession}\t${methylBedFile.file.accession}\t" +
                    "${methylBedFile.experiment.accession}\t${matchCriteria.assembly}\t${matchCriteria.bioSampleOntologyId}\t" +
                    "${matchCriteria.donorId}\t${matchCriteria.lifeStage}\t${matchCriteria.age}\t${matchCriteria.ageUnits}\n"
            writer.write(line)
        }
    }
}