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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file " +
                "that includes the assetlinks.json files to load must be declared in the manifest using a <meta-data> element.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    private val usages = mutableListOf<Location>()

    override fun getApplicableConstructorTypes(): List<String>? = listOf(
        "androidx.credentials.GetCredentialRequest",
        "android.credentials.GetCredentialRequest",
        "androidx.credentials.GetCredentialRequest.Builder",
        "android.credentials.GetCredentialRequest.Builder"
    )

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        usages.add(context.getLocation(node))
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Incremental state merging not required for this cross-file manifest validation.
    }

    override fun afterCheckRootProject(context: Context) {
        if (usages.isEmpty()) return

        val manifestFile = context.client.findManifest(context.project)
        if (manifestFile == null || !manifestFile.exists()) {
            usages.clear()
            return
        }

        var hasAssetStatements = false
        try {
            val document = context.client.getXmlParser().parse(manifestFile)
            val metaDataNodes = document.getElementsByTagName("meta-data")
            for (i in 0 until metaDataNodes.length) {
                val element = metaDataNodes.item(i) as? org.w3c.dom.Element ?: continue
                val name = element.getAttributeNS(ANDROID_URI, "name")
                if (name == "asset_statements") {
                    hasAssetStatements = true
                    break
                }
            }
        } catch (e: Exception) {
            // If manifest parsing fails, skip validation to avoid false positives
            usages.clear()
            return
        }

        if (!hasAssetStatements) {
            for (location in usages) {
                context.report(
                    ISSUE,
                    location,
                    "Missing Digital Asset Link configuration for Credential Manager. " +
                        "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                        "to your AndroidManifest.xml."
                )
            }
        }
        usages.clear()
    }
}