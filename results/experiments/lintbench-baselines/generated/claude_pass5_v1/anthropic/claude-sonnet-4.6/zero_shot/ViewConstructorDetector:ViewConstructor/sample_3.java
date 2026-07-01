package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.EnumSet;
import java.util.List;

/**
 * Detector for missing View constructors needed for XML inflation.
 */
public class ViewConstructorDetector extends Detector implements ClassScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a " +
            "constructor with one of the following signatures:\n" +
            " * `View(Context context)`\n" +
            " * `View(Context context, AttributeSet attrs)`\n" +
            " * `View(Context context, AttributeSet attrs, int defStyle)`\n" +
            "\n" +
            "If your custom view needs to perform initialization which does " +
            "not apply when used in a layout editor, you can surround the " +
            "given code with a check to see if `View#isInEditMode()` is " +
            "false, since that method will return `false` at runtime but " +
            "true within a user interface editor.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    ViewConstructorDetector.class,
                    Scope.CLASS_FILE_SCOPE));

    private static final String ANDROID_VIEW = "android/view/View";
    private static final String ANDROID_VIEW_GROUP = "android/view/ViewGroup";
    private static final String CONTEXT_DESC = "Landroid/content/Context;";
    private static final String ATTRIBUTE_SET_DESC = "Landroid/util/AttributeSet;";

    // Constructor descriptors for the three valid forms
    private static final String CONSTRUCTOR_CONTEXT =
            "(" + CONTEXT_DESC + ")V";
    private static final String CONSTRUCTOR_CONTEXT_ATTRS =
            "(" + CONTEXT_DESC + ATTRIBUTE_SET_DESC + ")V";
    private static final String CONSTRUCTOR_CONTEXT_ATTRS_STYLE =
            "(" + CONTEXT_DESC + ATTRIBUTE_SET_DESC + "I)V";

    /** Constructs a new {@link ViewConstructorDetector} */
    public ViewConstructorDetector() {
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        // Skip abstract classes - they don't need to be inflated directly
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        // Skip anonymous classes
        if (classNode.name != null && classNode.name.contains("$")) {
            // Check if it's an anonymous inner class (ends with $<number>)
            String simpleName = classNode.name;
            int dollarIndex = simpleName.lastIndexOf('$');
            if (dollarIndex >= 0) {
                String suffix = simpleName.substring(dollarIndex + 1);
                if (suffix.isEmpty() || Character.isDigit(suffix.charAt(0))) {
                    return;
                }
            }
        }

        // Only check classes that extend View or ViewGroup (directly or indirectly)
        if (!extendsView(context, classNode)) {
            return;
        }

        // Check if the class has at least one of the valid View constructors
        if (!hasValidViewConstructor(classNode)) {
            String message = "Custom view " + classNode.name.substring(classNode.name.lastIndexOf('/') + 1) +
                    " is missing constructor used by tools: " +
                    "(Context) or (Context,AttributeSet) or (Context,AttributeSet,int)";
            context.report(ISSUE, context.getLocation(classNode), message);
        }
    }

    /**
     * Checks whether the given class node has at least one of the three
     * valid View constructors.
     */
    private static boolean hasValidViewConstructor(ClassNode classNode) {
        @SuppressWarnings("unchecked")
        List<MethodNode> methods = classNode.methods;
        if (methods == null) {
            return false;
        }

        for (MethodNode method : methods) {
            if ("<init>".equals(method.name)) {
                String desc = method.desc;
                if (CONSTRUCTOR_CONTEXT.equals(desc)
                        || CONSTRUCTOR_CONTEXT_ATTRS.equals(desc)
                        || CONSTRUCTOR_CONTEXT_ATTRS_STYLE.equals(desc)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks whether the given class extends android.view.View (directly or indirectly).
     */
    private boolean extendsView(ClassContext context, ClassNode classNode) {
        String superName = classNode.superName;
        while (superName != null && !superName.equals("java/lang/Object")) {
            if (ANDROID_VIEW.equals(superName) || ANDROID_VIEW_GROUP.equals(superName)) {
                return true;
            }
            // Check if superName itself is a view subclass by looking it up
            ClassNode superClass = context.getDriver().findClass(context, superName, 0);
            if (superClass == null) {
                // We can't resolve the superclass; check if the name suggests it's a View
                // by checking common patterns
                if (superName.startsWith("android/view/")
                        || superName.startsWith("android/widget/")
                        || superName.startsWith("android/webkit/")) {
                    return true;
                }
                break;
            }
            superName = superClass.superName;
        }
        return false;
    }
}