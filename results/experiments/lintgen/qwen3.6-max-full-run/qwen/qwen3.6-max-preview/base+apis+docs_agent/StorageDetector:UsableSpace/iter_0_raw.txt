package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.io.File

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = """
                When you need to allocate disk space for large files, consider using the new \
                `allocateBytes(FileDescriptor, long)` API, which will automatically clear \
                cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, \
                call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since \
                the former will consider any cached data that the system is willing to \
                clear on your behalf.

                Note that these methods require API level 26. If your app is running on \
                older devices, you will probably need to use both APIs, conditionally switching \
                on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to \
                see if you are already using both APIs, so if it warns even though you are \
                already using the new API, consider moving the calls to the same file or \
                suppressing the warning.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        private val NEW_API_METHODS = setOf("getAllocatableBytes", "allocateBytes")
    }

    private val fileUsesNewApi = mutableMapOf<File, Boolean>()

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "getUsableSpace") return

                val method = node.resolve() ?: return
                if (method.containingClass?.qualifiedName != "java.io.File") return

                val file = context.file
                val usesNew = fileUsesNewApi.getOrPut(file) {
                    checkFileForNewApis(context)
                }

                if (!usesNew) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use `getAllocatableBytes()` instead of `getUsableSpace()`"
                    )
                }
            }
        }
    }

    private fun checkFileForNewApis(context: JavaContext): Boolean {
        var found = false
        context.uastFile?.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName in NEW_API_METHODS) {
                    found = true
                    return true
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }

    override fun afterCheckEachProject(context: Context) {
        fileUsesNewApi.clear()
    }
}