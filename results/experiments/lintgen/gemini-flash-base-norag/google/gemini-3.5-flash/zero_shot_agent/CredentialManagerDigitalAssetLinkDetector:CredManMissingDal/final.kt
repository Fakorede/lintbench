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
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner, SourceCodeScanner {

    private var usesCredentialManagerPassword = false
    private var hasMetaData = false
    private var metaDataHasResource = false
    private var assetResourceName: String? = null
    private val stringResources = mutableMapOf<String, Pair<String, Location>>()

    private var manifestLocation: Location? = null
    private var metaDataLocation: Location? = null
    private var firstUsageLocation: Location? = null

    override fun beforeCheckProject(context: Context) {
        usesCredentialManagerPassword = false
        hasMetaData = false
        metaDataHasResource = false
        assetResourceName = null
        stringResources.clear()
        manifestLocation = null
        metaDataLocation = null
        firstUsageLocation = null
    }

    // XML Scanner implementation
    override fun getApplicableElements(): Collection<String> {
        return listOf("application", "meta-data", "string")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                manifestLocation = context.getLocation(element)
            }
            "meta-data" -> {
                val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == "asset_statements") {
                    hasMetaData = true
                    metaDataLocation = context.getLocation(element)
                    val resource = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_RESOURCE)
                    if (resource.isNotEmpty()) {
                        metaDataHasResource = true
                        assetResourceName = resource.substringAfter('/')
                    }
                }
            }
            "string" -> {
                val name = element.getAttribute(SdkConstants.ATTR_NAME)
                if (name.isNotEmpty()) {
                    stringResources[name] = Pair(element.textContent ?: "", context.getLocation(element))
                }
            }
        }
    }

    // Source Code Scanner implementation
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(USimpleNameReferenceExpression::class.java, UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiClass) {
                    val qualifiedName = resolved.qualifiedName
                    if (isTargetClass(qualifiedName)) {
                        markUsage(context, node)
                    }
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName
                if (isTargetClass(qualifiedName)) {
                    markUsage(context, node)
                }
            }
        }
    }

    private fun isTargetClass(qualifiedName: String?): Boolean {
        return qualifiedName == "androidx.credentials.GetPasswordOption" ||
                qualifiedName == "androidx.credentials.CreatePasswordRequest" ||
                qualifiedName == "androidx.credentials.PasswordCredential" ||
                qualifiedName == "androidx.credentials.CredentialManager"
    }

    private fun markUsage(context: JavaContext, node: UElement) {
        usesCredentialManagerPassword = true
        if (firstUsageLocation == null) {
            firstUsageLocation = context.getLocation(node)
        }
    }

    override fun afterCheckProject(context: Context) {
        if (!usesCredentialManagerPassword) {
            return
        }

        if (!hasMetaData) {
            val location = manifestLocation ?: firstUsageLocation ?: Location.create(context.project.dir)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link declaration in AndroidManifest.xml for Credential Manager password sign-in"
            )
            return
        }

        if (!metaDataHasResource) {
            val location = metaDataLocation ?: manifestLocation ?: firstUsageLocation ?: Location.create(context.project.dir)
            context.report(
                ISSUE,
                location,
                "Missing `android:resource` attribute in `<meta-data>` tag for Digital Asset Link"
            )
            return
        }

        val resName = assetResourceName ?: "asset_statements"
        val stringInfo = stringResources[resName]
        if (stringInfo == null) {
            val location = metaDataLocation ?: manifestLocation ?: firstUsageLocation ?: Location.create(context.project.dir)
            context.report(
                ISSUE,
                location,
                "Missing string resource `$resName` for Digital Asset Link"
            )
            return
        }

        val (text, stringLoc) = stringInfo
        val hasInclude = text.contains("\"include\"") || text.contains("'include'")
        val hasUrl = text.contains("https://") || text.contains("http://")

        if (!hasInclude) {
            context.report(
                ISSUE,
                stringLoc,
                "Digital Asset Link string resource is missing the `include` statement"
            )
        } else if (!hasUrl) {
            context.report(
                ISSUE,
                stringLoc,
                "Digital Asset Link string resource is missing a valid URL"
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
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
        )
    }
}