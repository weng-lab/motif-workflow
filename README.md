# Motif Workflow

Simple workflow that runs MEME-Suite's MEME and TomTom in 
[factobook-meme](https://github.com/krews-community/factorbook-meme) on entire encode datasets. Encode datasets are 
looked up on the fly using the ENCODE API.

## Modes
Has two modes, Chip-Seq and Methyl.

### Chip-Seq Mode
Chip-Seq mode runs against every released ENCODE Chip-Seq experiment.

### Methyl Mode
Methyl mode runs against every released ENCODE WGBS experiment with applicable bed-methyl files. 

## Configuration
Krews workflows are run with a configuration file argument `--config path/to/my-config.conf`. Two example configurations
are provided in the `config` directory, one for chip-seq mode and one for methyl.

### Workflow Configurations
* `methyl-mode` - Toggles use of methyl mode if true, and chip-seq otherwise. Default value is false.

### Motif Task Configurations
* `methyl-percent-threshold` - Percentage over which we will use a methylation site from the methylation bed file. 
Only useful while in methyl mode.

### TomTom Task Configurations
* `threshold` - The TomTom threshold argument. From the docs: "Only matches for which the significance is less 
than or equal to the threshold set by the -thresh option (below) will be output."
* `comparison-databases` - A list of motif database files to compare against.

## Building

Just run `scripts/build.sh` to build. You should find your executable Jar in `build/$APP_NAME-$VERSION-exec.jar`

## Running

Once built, you can run `scripts/run.sh` with any Krews args. It's just a convenience 
wrapper for `java -jar build/*.jar`. 