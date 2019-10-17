package util

val URL_ASSEMBLY_REPLACEMENTS = mapOf("GRCh38" to "hg38")

fun assemblyUrl(assembly: String): String {
    val urlAssembly = URL_ASSEMBLY_REPLACEMENTS.getOrDefault(assembly, assembly)
    return "https://hgdownload-test.gi.ucsc.edu/goldenPath/$urlAssembly/bigZips/$urlAssembly.2bit"
}

fun chromeSizesUrl(assembly: String): String {
    val urlAssembly = URL_ASSEMBLY_REPLACEMENTS.getOrDefault(assembly, assembly)
    return "https://hgdownload-test.gi.ucsc.edu/goldenPath/$urlAssembly/bigZips/$urlAssembly.chrom.sizes"
}