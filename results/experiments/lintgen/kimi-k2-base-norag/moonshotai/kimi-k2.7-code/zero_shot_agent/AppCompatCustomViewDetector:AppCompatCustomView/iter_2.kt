package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class AppCompatCustomViewDetector : Detector(), Detector.ClassScanner {

    override fun getApplicableCallOwners(): List<String>? = null

    override fun checkSameOwner(): Boolean = false

    override fun visitClass(context: ClassContext, classNode: ClassNode) {
        val superName = classNode.superName ?: return
        if (!superName.startsWith(ANDROID_WIDGET_PREFIX_INTERNAL)) return

        val widgetName = superName.substring(ANDROID_WIDGET_PREFIX_INTERNAL.length)
        val replacement = WIDGET_TO_APPCOMPAT[widgetName] ?: return

        val ownName = classNode.name
        if (ownName != null &&
            ownName.startsWith(APPCOMPAT_PREFIX_INTERNAL) &&
            ownName.endsWith(replacement)
        ) {
            return
        }

        val message = "This custom view should extend $APPCOMPAT_PREFIX$replacement instead of $widgetName."
        context.report(ISSUE, classNode, context.getLocation(classNode), message)
    }

    override fun visitMethod(context: ClassContext, methodNode: MethodNode) {}
    override fun visitField(context: ClassContext, fieldNode: FieldNode) {}
    override fun visitInstruction(
        context: ClassContext,
        methodNode: MethodNode,
        instruction: AbstractInsnNode
    ) {}

    companion object {
        private const val ANDROID_WIDGET_PREFIX = "android.widget."
        private const val ANDROID_WIDGET_PREFIX_INTERNAL = "android/widget/"
        private const val APPCOMPAT_PREFIX = "androidx.appcompat.widget."
        private const val APPCOMPAT_PREFIX_INTERNAL = "androidx/appcompat/widget/"

        private val WIDGET_TO_APPCOMPAT = mapOf(
            "TextView" to "AppCompatTextView",
            "EditText" to "AppCompatEditText",
            "Button" to "AppCompatButton",
            "ImageButton" to "AppCompatImageButton",
            "ImageView" to "AppCompatImageView",
            "CheckBox" to "AppCompatCheckBox",
            "RadioButton" to "AppCompatRadioButton",
            "Spinner" to "AppCompatSpinner",
            "Switch" to "AppCompatSwitch",
            "SeekBar" to "AppCompatSeekBar",
            "AutoCompleteTextView" to "AppCompatAutoCompleteTextView",
            "MultiAutoCompleteTextView" to "AppCompatMultiAutoCompleteTextView",
            "CheckedTextView" to "AppCompatCheckedTextView",
            "RatingBar" to "AppCompatRatingBar",
            "ToggleButton" to "AppCompatToggleButton"
        )

        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will automatically
                load special appcompat replacements for the builtin widgets. However, this does not work
                for your own custom views.

                Instead of extending the `android.widget` classes directly, you should instead extend one
                of the delegate classes in `androidx.appcompat.widget` (such as `AppCompatTextView`).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppCompatCustomViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}