package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.utils.XmlUtils
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element
import java.io.StringReader

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    private var usesCredentialManager = false

    override fun beforeCheckEachProject(context: Context) {
        usesCredentialManager = false
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                checkUsage(node.resolve())
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                checkUsage(node.resolve())
            }

            private fun checkUsage(psi: Any?) {
                if (usesCredentialManager) return
                val qName = when (psi) {
                    is PsiMethod -> psi.containingClass?.qualifiedName
                    is PsiClass -> psi.qualifiedName
                    else -> null
                }
                if (qName != null && (
                        qName == "androidx.credentials.CredentialManager" ||
                        qName == "androidx.credentials.GetPasswordRequest" ||
                        qName == "androidx.credentials.GetCredentialRequest"
                    )) {
                    usesCredentialManager = true
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!usesCredentialManager) return

        val manifestFile = context.project.manifestFile ?: return
        val content = try {
            manifestFile.readText()
        } catch (e: Exception) {
            return
        }
        val document = try {
            XmlUtils.parseDocument(StringReader(content), true)
        } catch (e: Exception) {
            return
        } ?: return

        val root = document.documentElement ?: return
        val applicationNodes = root.getElementsByTagName(SdkConstants.TAG_APPLICATION)
        if (applicationNodes.length == 0) return
        val application = applicationNodes.item(0) as? Element ?: return

        val metaDatas = application.getElementsByTagName(SdkConstants.TAG_META_DATA)
        var hasAssetStatements = false
        for (i in 0 until metaDatas.length) {
            val metaData = metaDatas.item(i) as? Element ?: continue
            val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "asset_statements") {
                hasAssetStatements = true
                break
            }
        }

        if (!hasAssetStatements) {
            context.report(
                ISSUE,
                Location.create(manifestFile),
                "Missing Digital Asset Link configuration for Credential Manager. " +
                        "Add `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
                        "to your `<application>` tag in the manifest."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string resource file \
                that includes the `assetlinks.json` files to load must be declared in the manifest using a \
                `<meta-data>` element.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}