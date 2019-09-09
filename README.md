# Motif Workflow

Single task workflow for running factorbook-meme on entire encode dataset.

## Building

Just run `scripts/build.sh` to build. You should find your executable Jar in `build/$APP_NAME-$VERSION-exec.jar`

## Running

Once built, you can run `scripts/run.sh` with any Krews args. It's just a convenience 
wrapper for `java -jar build/*.jar`. 