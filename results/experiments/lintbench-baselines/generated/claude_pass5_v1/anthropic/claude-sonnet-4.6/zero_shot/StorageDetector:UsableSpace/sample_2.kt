package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.AbstractUastVisitor

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"

        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using `getUsableSpace()`",
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
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                StorageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    /**
     * Tracks whether the current file uses the newer storage allocation APIs.
     */
    private var usesNewApi = false

    /**
     * Collects all call expressions that use getUsableSpace so we can
     * report them after we've finished scanning the whole file.
     */
    private val usableSpaceCalls = mutableListOf<UCallExpression>()

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_USABLE_SPACE, GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)
    }

    override fun beforeCheckFile(context: Context) {
        usesNewApi = false
        usableSpaceCalls.clear()
    }

    override fun afterCheckFile(context: Context) {
        if (!usesNewApi && usableSpaceCalls.isNotEmpty()) {
            val javaContext = context as? JavaContext ?: return
            for (call in usableSpaceCalls) {
                javaContext.report(
                    ISSUE,
                    call,
                    javaContext.getCallLocation(call, includeReceiver = true, includeArguments = false),
                    "Consider using `getAllocatableBytes(UUID)` and `allocateBytes(FileDescriptor, long)` instead of `getUsableSpace()`"
                )
            }
        }
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (node.methodName) {
            GET_USABLE_SPACE -> {
                usableSpaceCalls.add(node)
            }
            GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES -> {
                usesNewApi = true
            }
        }
    }
}