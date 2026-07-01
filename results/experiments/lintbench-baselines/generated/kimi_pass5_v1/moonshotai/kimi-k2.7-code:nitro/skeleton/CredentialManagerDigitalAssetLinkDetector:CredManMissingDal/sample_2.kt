package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val ASSET_STATEMENTS_NAME = "asset_statements"

        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password-based sign-in with Credential Manager, you must declare
                a `<meta-data android:name="asset_statements" android:resource="@string/asset_statements" />`
                element in the `<application>` section of your `AndroidManifest.xml`, and provide a
                corresponding string resource that lists the Digital Asset Links
                `assetlinks.json` files to load.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    private data class StoredLocation(val path: String, val line: Int)

    private val pendingReports = mutableListOf<StoredLocation>()

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("androidx.credentials.GetPasswordOption")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val location = context.getLocation(node)
        val line = location.start?.line ?: 1
        val entry = "${context.file.absolutePath}|$line"
        context.getPartialResults(ISSUE).add(context.project, entry)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        for (project in context.driver.allProjects) {
            for (entry in partialResults.getProjectList(project)) {
                val parts = entry.split('|', limit = 2)
                if (parts.size == 2) {
                    pendingReports.add(
                        StoredLocation(parts[0], parts[1].toIntOrNull() ?: 1)
                    )
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (pendingReports.isEmpty()) return

        if (hasDigitalAssetLinkMetaData(context)) {
            pendingReports.clear()
            return
        }

        for (stored in pendingReports) {
            context.report(
                ISSUE,
                createLineLocation(stored.path, stored.line),
                "Missing Digital Asset Link meta-data for Credential Manager password sign-in. " +
                    "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />`."
            )
        }

        pendingReports.clear()
    }

    private fun hasDigitalAssetLinkMetaData(context: Context): Boolean =
        context.mainProject.manifestFiles.any { manifest -> hasAssetStatementsMetaData(manifest) }

    private fun hasAssetStatementsMetaData(manifest: java.io.File): Boolean {
        if (!manifest.isFile) return false

        val doc = try {
            XmlUtils.parseUtf8File(manifest, false)
        } catch (e: Throwable) {
            return false
        } ?: return false

        val application = doc.documentElement
            ?.getElementsByTagName("application")
            ?.item(0) as? org.w3c.dom.Element
            ?: return false

        val metaNodes = application.getElementsByTagName("meta-data")
        for (i in 0 until metaNodes.length) {
            val meta = metaNodes.item(i) as? org.w3c.dom.Element ?: continue

            val name = meta.getAttributeNS(ANDROID_NS, "name")
                .ifEmpty { meta.getAttribute("android:name") }
                .ifEmpty { meta.getAttribute("name") }

            val resource = meta.getAttributeNS(ANDROID_NS, "resource")
                .ifEmpty { meta.getAttribute("android:resource") }
                .ifEmpty { meta.getAttribute("resource") }

            if (name == ASSET_STATEMENTS_NAME && resource.startsWith("@string/")) {
                return true
            }
        }

        return false
    }

    private fun createLineLocation(path: String, line: Int): Location {
        val file = java.io.File(path)
        if (!file.isFile) {
            return Location.create(file)
        }

        val contents = try {
            file.readText()
        } catch (e: Throwable) {
            return Location.create(file)
        }

        val targetLine = line.coerceAtLeast(1)
        var currentLine = 1
        var lineStart = 0

        while (currentLine < targetLine && lineStart < contents.length) {
            val nextNewline = contents.indexOf('\n', lineStart)
            if (nextNewline == -1) break
            lineStart = nextNewline + 1
            currentLine++
        }

        val lineEnd = contents.indexOf('\n', lineStart).let {
            if (it == -1) contents.length else it
        }

        return Location.create(
            file,
            contents,
            lineStart.coerceAtMost(contents.length),
            lineEnd.coerceAtMost(contents.length)
        )
    }
}