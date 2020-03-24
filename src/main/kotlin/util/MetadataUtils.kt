package util

import MotifBed
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

fun writeConservationMetadataFile(metadataPath: Path, experimentFiles: List<MotifBed>) {
    Files.newBufferedWriter(metadataPath).use { writer ->
        writer.write("#peaks_accession\tdataset_accession\tmotif\tassembly\n")
        for(motifBed in experimentFiles) {
            writer.write("${motifBed.peaksData.experimentFile.file.accession}\t${motifBed.peaksData.experimentFile.experiment.accession}\t${motifBed.bedFile.filenameNoExt()}\t${motifBed.peaksData.assembly}\n")
        }
    }
}

fun writeHistoneMetadataFile(metadataPath: Path, histoneSignalMatches: List<Triple<MotifBed, Set<EncodeFileWithExp>, ExperimentMatchCriteria>>) {
    Files.newBufferedWriter(metadataPath).use { writer ->
        val header = "#peaks_accession\tdataset_accession\thistone_dataset_accession\thistone_bigwig_accessions\t" +
                "assembly\tbiosample_ontology_id\tdonor\tlife_stage\tage\tage_units\n"
        writer.write(header)
        for((motifBed, histoneSignals, matchCriteria) in histoneSignalMatches) {
            histoneSignals.forEach{ histoneSignal ->
                val line = "${motifBed.peaksData.experimentFile.file.accession}\t${motifBed.peaksData.experimentFile.experiment.accession}\t${histoneSignal.experiment.accession}\t" +
                        "${histoneSignal.file.accession}\t${matchCriteria.assembly}\t${matchCriteria.bioSampleOntologyId}\t${matchCriteria.donorId}\t" +
                        "${matchCriteria.lifeStage}\t${matchCriteria.age}\t${matchCriteria.ageUnits}\n"
                writer.write(line)
            }
        }
    }
}

fun writeDnaseMetadataFile(metadataPath: Path, dnaseAlignmentMatches: List<Triple<MotifBed, EncodeFileWithExp, ExperimentMatchCriteria>>) {
    Files.newBufferedWriter(metadataPath).use { writer ->
        val header = "#peaks_accession\tdataset_accession\tdnase_dataset_accession\tdnase_bam_accessions\t" +
                "assembly\tbiosample_ontology_id\tdonor\tlife_stage\tage\tage_units\n"
        writer.write(header)
        for((motifBed, dnaseAlignment, matchCriteria) in dnaseAlignmentMatches) {
            val line = "${motifBed.peaksData.experimentFile.file.accession}\t${motifBed.peaksData.experimentFile.experiment.accession}\t${dnaseAlignment.experiment.accession}\t" +
                    "${dnaseAlignment.file.accession}\t${matchCriteria.assembly}\t${matchCriteria.bioSampleOntologyId}\t${matchCriteria.donorId}\t" +
                    "${matchCriteria.lifeStage}\t${matchCriteria.age}\t${matchCriteria.ageUnits}\n"
            writer.write(line)
        }
    }
}
