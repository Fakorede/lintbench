package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.io.File

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    private val localCalls = mutableListOf<Location>()
    private var checkPartialResultsCalled = false

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string \
                resource file that includes the `assetlinks.json` files to load must be declared \
                in the manifest using a `<meta-data>` element.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }

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
        val location = context.getLocation(node)
        localCalls.add(location)

        val map = context.getPartialResults(ISSUE).map()
        val existing = map.get("calls") ?: ""
        val path = location.file.absolutePath
        val start = location.start?.offset ?: -1
        val end = location.end?.offset ?: -1
        val entry = "$path:$start:$end"
        val newCalls = if (existing.isEmpty()) entry else "$existing;$entry"
        map.put("calls", newCalls)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        checkPartialResultsCalled = true
        if (hasAssetStatements(context)) {
            return
        }

        for (project in partialResults.projects) {
            val map = partialResults.getMap(project)
            val calls = map.get("calls") ?: continue
            if (calls.isNotEmpty()) {
                for (call in calls.split(";")) {
                    val parts = call.split(":")
                    if (parts.size == 3) {
                        val path = parts[0]
                        val start = parts[1].toIntOrNull() ?: -1
                        val end = parts[2].toIntOrNull() ?: -1
                        val file = File(path)
                        if (file.exists()) {
                            val content = try { file.readText() } catch (e: Exception) { "" }
                            val location = if (start >= 0 && end >= 0 && content.isNotEmpty()) {
                                Location.create(file, content, start, end)
                            } else {
                                Location.create(file)
                            }
                            reportIncident(context, location)
                        }
                    }
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!checkPartialResultsCalled && localCalls.isNotEmpty()) {
            if (!hasAssetStatements(context)) {
                for (location in localCalls) {
                    reportIncident(context, location)
                }
            }
        }
    }

    private fun reportIncident(context: Context, location: Location) {
        val incident = Incident(
            ISSUE,
            location,
            "Missing Digital Asset Link in manifest. When using password sign-in, " +
            "you must declare an asset statements string resource file in the manifest using a <meta-data> element."
        )
        context.report(incident)
    }

    private fun hasAssetStatements(context: Context): Boolean {
        val mainProject = context.mainProject
        val document = mainProject.mergedManifest ?: return false
        val root = document.documentElement ?: return false
        val applicationList = root.getElementsByTagName("application")
        for (i in 0 until applicationList.length) {
            val application = applicationList.item(i) as? org.w3c.dom.Element ?: continue
            val metaDataList = application.getElementsByTagName("meta-data")
            for (j in 0 until metaDataList.length) {
                val metaData = metaDataList.item(j) as? org.w3c.dom.Element ?: continue
                val name = metaData.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                    .takeIf { it.isNotEmpty() }
                    ?: metaData.getAttribute("android:name")
                if (name == "asset_statements") {
                    return true
                }
            }
        }
        return false
    }
}