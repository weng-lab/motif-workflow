import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import util.*
import java.nio.file.Files
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

@Disabled class EncodeUtilsTests {

    @Test fun `Test chipSeqBedFiles`() {
        val chipSeqBedFiles = chipSeqBedFiles()
        assertThat(chipSeqBedFiles).isNotEmpty
    }

    @Test fun `Test methylBedFiles`() {
        val bedMethylFiles = methylBedFiles()
        assertThat(bedMethylFiles).isNotEmpty
    }

    @Test fun `Test methylBedMatches`() {
        val methylBedMatches = methylBedMatches()

        val metadataPath = Files.createTempFile("metadata", ".tsv")
        writeMethylMetadataFile(metadataPath, methylBedMatches)
        log.info { "methyl bed matches written to $metadataPath" }

        val hasMatchingExample = methylBedMatches.any {
            it.chipSeqFile.file.accession == "ENCFF981HPG" && it.methylBedFile.file.accession == "ENCFF550FZT"
        }
        assertThat(hasMatchingExample).isEqualTo(true)
    }

}