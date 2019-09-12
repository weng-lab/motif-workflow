import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import util.experimentFiles

@Disabled class EncodeUtilsTests {

    @Test fun `Test experimentFiles`() {
        val experimentFiles = experimentFiles()
        assertThat(experimentFiles).isNotEmpty
    }

}