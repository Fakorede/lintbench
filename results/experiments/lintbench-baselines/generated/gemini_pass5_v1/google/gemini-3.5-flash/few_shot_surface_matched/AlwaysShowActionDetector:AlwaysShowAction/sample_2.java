package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UReferenceExpression;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                            + "Java code is usually a deviation from the user interface style guide. Use "
                            + "`ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                            + "If `always` is used sparingly there are usually no problems and behavior is "
                            + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                            + "items. Using it more than twice in the same menu is a bad idea.\n\n"
                            + "This check looks for menu XML files that contain more than two `always` "
                            + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                            + "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    private final java.util.List<org.w3c.dom.Attr> mAlwaysAttributes = new java.util.ArrayList<>();
    private boolean mHasIfRoom = false;

    private static class JavaReference {
        final JavaContext context;
        final UReferenceExpression reference;

        JavaReference(JavaContext context, UReferenceExpression reference) {
            this.context = context;
            this.reference = reference;
        }
    }

    private final java.util.List<JavaReference> mAlwaysJavaReferences = new java.util.ArrayList<>();
    private boolean mHasIfRoomJava = false;

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MENU;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@com.android.annotations.NonNull XmlContext context) {
        mAlwaysAttributes.clear();
        mHasIfRoom = false;
    }

    @Override
    public void visitAttribute(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value.contains("always")) {
            mAlwaysAttributes.add(attribute);
        }
        if (value.contains("ifRoom")) {
            mHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckFile(@com.android.annotations.NonNull XmlContext context) {
        if (mAlwaysAttributes.size() > 2) {
            for (org.w3c.dom.Attr attribute : mAlwaysAttributes) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Prefer \"`ifRoom`\" instead of \"`always`\" (more than two \"`always`\" actions in this menu)");
            }
        } else if (mAlwaysAttributes.size() > 0 && !mHasIfRoom) {
            for (org.w3c.dom.Attr attribute : mAlwaysAttributes) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Prefer \"`ifRoom`\" instead of \"`always`\"");
            }
        }
    }

    @Override
    public java.util.List<String> getApplicableReferenceNames() {
        return java.util.Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UReferenceExpression reference,
            @com.android.annotations.NonNull PsiElement referenced) {
        String name = reference.getResolvedName();
        if (name == null) {
            name = reference.asSourceString();
            if (name.contains(".")) {
                name = name.substring(name.lastIndexOf('.') + 1);
            }
        }

        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            if (isMenuItemReference(referenced)) {
                mAlwaysJavaReferences.add(new JavaReference(context, reference));
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            if (isMenuItemReference(referenced)) {
                mHasIfRoomJava = true;
            }
        }
    }

    private boolean isMenuItemReference(PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                return "android.view.MenuItem".equals(qualifiedName);
            }
        }
        return true;
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        if (!mAlwaysJavaReferences.isEmpty() && !mHasIfRoomJava) {
            for (JavaReference ref : mAlwaysJavaReferences) {
                ref.context.report(
                        ISSUE,
                        ref.reference,
                        ref.context.getLocation(ref.reference),
                        "Prefer \"`SHOW_AS_ACTION_IF_ROOM`\" instead of \"`SHOW_AS_ACTION_ALWAYS`\"");
            }
        }
        mAlwaysJavaReferences.clear();
        mHasIfRoomJava = false;
    }
}