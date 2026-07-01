package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    private var hasAssetStatements: Boolean? = null

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!checkAssetStatements(context.project)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Missing Digital Asset Link for Credential Manager. An `asset_statements` `<meta-data>` tag must be declared in the manifest."
            )
        }
    }

    private fun checkAssetStatements(project: Project): Boolean {
        val cached = hasAssetStatements
        if (cached != null) return cached

        val result = isAssetStatementsDeclared(project)
        hasAssetStatements = result
        return result
    }

    private fun isAssetStatementsDeclared(project: Project): Boolean {
        val merged = project.mergedManifest
        if (merged != null && hasAssetStatementsInDocument(merged)) {
            return true
        }
        for (file in project.manifestFiles) {
            try {
                if (file.exists()) {
                    val text = file.readText()
                    val document = parseDocument(text)
                    if (document != null && hasAssetStatementsInDocument(document)) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return false
    }

    private fun hasAssetStatementsInDocument(document: Document): Boolean {
        val root = document.documentElement ?: return false
        val applicationList = root.getElementsByTagName("application")
        for (i in 0 until applicationList.length) {
            val application = applicationList.item(i) as? Element ?: continue
            val metaDataList = application.getElementsByTagName("meta-data")
            for (j in 0 until metaDataList.length) {
                val metaData = metaDataList.item(j) as? Element ?: continue
                val name = metaData.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                    .ifEmpty { metaData.getAttribute("android:name") }
                if (name == "asset_statements") {
                    return true
                }
            }
        }
        return false
    }

    private fun parseDocument(text: String): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            builder.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string \
                resource file that includes the `assetlinks.json` files to load must be declared \
                in the manifest using a `<meta-data>` element.
                """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}