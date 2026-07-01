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
        private const val KEY_USED = "used"
        private const val KEY_LOCATION = "location"
        private const val ASSET_STATEMENTS = "asset_statements"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private val PASSWORD_OPTION_CLASSES = listOf(
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest",
        )

        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                Credential Manager's password sign-in flow requires a Digital Asset Link
                statement so the provider can associate the app with a website. Declare it
                with a `<meta-data android:name="asset_statements"
                android:resource="@string/asset_statements"/>` element inside the
                `<application>` tag of AndroidManifest.xml, and make the referenced string
                resource contain the assetlinks.json URL(s).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? = PASSWORD_OPTION_CLASSES

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val partial = context.getPartialResults(ISSUE, context.mainProject).map()
        partial[KEY_USED] = true
        if (partial[KEY_LOCATION] == null) {
            partial[KEY_LOCATION] = context.getLocation(node)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // The manifest check and final report are performed in afterCheckRootProject so the
        // Digital Asset Link requirement can be evaluated against the root application module.
    }

    override fun afterCheckRootProject(context: Context) {
        val partial = context.getPartialResults(ISSUE, context.mainProject).map()
        if (partial[KEY_USED] != true) {
            return
        }

        if (hasAssetStatementsMetaData(context)) {
            return
        }

        val location = partial[KEY_LOCATION] as? Location
            ?: context.mainProject.manifestFiles.firstOrNull()?.let { Location.create(it) }
            ?: Location.create(context.mainProject.dir)

        context.report(
            ISSUE,
            location,
            "Missing Digital Asset Link for Credential Manager password sign-in. " +
                "Add a `<meta-data android:name=\"asset_statements\" " +
                "android:resource=\"@string/asset_statements\"/>` element to the " +
                "`<application>` tag in AndroidManifest.xml.",
        )
    }

    private fun hasAssetStatementsMetaData(context: Context): Boolean {
        val project = context.mainProject
        val manifests = project.manifestFiles
        if (manifests.isEmpty()) {
            return false
        }

        val parser = context.client.xmlParser
        for (manifestFile in manifests) {
            val document = parser.parseXml(manifestFile, project) as? org.w3c.dom.Document
                ?: continue

            val nodes = document.getElementsByTagName("meta-data")
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? org.w3c.dom.Element ?: continue

                val name = element.getAttributeNS(ANDROID_NS, "name")
                    .takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("name")

                val resource = element.getAttributeNS(ANDROID_NS, "resource")
                    .takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("resource")

                if (name == ASSET_STATEMENTS && resource.startsWith("@string/")) {
                    return true
                }
            }
        }

        return false
    }
}