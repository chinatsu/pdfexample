/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package pdfexample

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

typealias Document = Pair<String, String>

val fontPath: Path = Paths.get("fonts/")
val docPath: Path = Paths.get("documents/")

val fonts: List<FontMetadata> = ObjectMapper()
    .registerKotlinModule()
    .readValue(Files.newInputStream(fontPath.resolve("config.json")))
val colorProfile: ByteArray = IOUtils.toByteArray(PDDocument::class.java.getResourceAsStream("/sRGB2014.icc"))


fun main() {
    Files.list(docPath)
        .map { it.fileName.toString().split('.').first() to Files.readAllBytes(it).toString(Charsets.UTF_8) }
        .toList()
        .forEach(Document::makePdf)
}

fun Document.makePdf() {
    val name = this.first
    val doc = this.second
    println("\nCreating PDF from $name.html")
    val outputStream = ByteArrayOutputStream()
    PdfRendererBuilder()
        .apply {
            for (font in fonts) {
                useFont({ ByteArrayInputStream(font.bytes) }, font.family, font.weight, font.style, font.subset)
            }
        }
        .usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_U)
        .useColorProfile(colorProfile)
        .withHtmlContent(doc, null)
        .toStream(outputStream)
        .run()
    val pdf = outputStream.toByteArray()
    println("PDF/A-2U compliant: ${pdf.verifyCompliance()}")
    File("$name.pdf").writeBytes(pdf)
}

fun ByteArray.verifyCompliance(flavour: PDFAFlavour = PDFAFlavour.PDFA_2_U): Boolean {
    VeraGreenfieldFoundryProvider.initialise()
    val validator = Foundries.defaultInstance().createValidator(flavour, false)
    val result = Foundries.defaultInstance().createParser(ByteArrayInputStream(this))
        .use { validator.validate(it) }
    val failures = result.testAssertions
        .filter { it.status != TestAssertion.Status.PASSED }
    failures.forEach { test ->
        println(test.message)
        println("Location ${test.location.context} ${test.location.level}")
        println("Status ${test.status}")
        println("Test number ${test.ruleId.testNumber}")
    }
    return failures.isEmpty()
}

data class FontMetadata(
    val family: String,
    val path: String,
    val weight: Int,
    val style: BaseRendererBuilder.FontStyle,
    val subset: Boolean
) {
    val bytes: ByteArray = Files.readAllBytes(fontPath.resolve(path))
}
