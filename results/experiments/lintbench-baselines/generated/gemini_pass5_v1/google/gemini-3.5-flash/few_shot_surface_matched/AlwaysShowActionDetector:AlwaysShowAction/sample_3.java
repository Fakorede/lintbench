package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UReferenceExpression;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                            + "Java code is usually a deviation from the user interface style guide. Use "
                            + "`ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
                            + "\n"
                            + "If `always` is used sparingly there are usually no problems and behavior is "
                            + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                            + "items. Using it more than twice in the same menu is a bad idea.\n"
                            + "\n"
                            + "This check looks for menu XML files that contain more than two `always` "
                            + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                            + "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES));

    private final java.util.List<Location> mAlwaysReferences = new java.util.ArrayList<>();
    private boolean mHasIfRoomReference = false;

    private java.util.List<org.w3c.dom.Attr> mAlwaysAttributes;
    private boolean mHasIfRoom;

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MENU;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@com.android.annotations.NonNull Context context) {
        if (context instanceof XmlContext) {
            mAlwaysAttributes = new java.util.ArrayList<>();
            mHasIfRoom = false;
        }
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
    public void afterCheckFile(@com.android.annotations.NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (mAlwaysAttributes != null) {
                int alwaysCount = mAlwaysAttributes.size();
                if (alwaysCount > 2) {
                    for (org.w3c.dom.Attr attribute : mAlwaysAttributes) {
                        xmlContext.report(
                                ISSUE,
                                attribute,
                                xmlContext.getLocation(attribute),
                                "Prefer \"ifRoom\" instead of \"always\" (using \"always\" more than twice in standard menus is a bad idea)");
                    }
                } else if (alwaysCount > 0 && !mHasIfRoom) {
                    for (org.w3c.dom.Attr attribute : mAlwaysAttributes) {
                        xmlContext.report(
                                ISSUE,
                                attribute,
                                xmlContext.getLocation(attribute),
                                "Prefer \"ifRoom\" instead of \"always\" (using \"always\" without \"ifRoom\" is usually a deviation from the style guide)");
                    }
                }
            }
        }
    }

    @Override
    public java.util.List<String> getApplicableReferenceNames() {
        return java.util.Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UReferenceExpression referenceExpression, @com.android.annotations.NonNull PsiElement referenced) {
        String name = referenceExpression.getResolvedName();
        if (name == null) {
            if (referenced instanceof PsiField) {
                name = ((PsiField) referenced).getName();
            }
        }
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mAlwaysReferences.add(context.getLocation(referenceExpression));
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mHasIfRoomReference = true;
        }
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        if (!mAlwaysReferences.isEmpty() && !mHasIfRoomReference) {
            for (Location location : mAlwaysReferences) {
                context.report(
                        ISSUE,
                        location,
                        "Prefer \"SHOW_AS_ACTION_IF_ROOM\" instead of \"SHOW_AS_ACTION_ALWAYS\" (using \"SHOW_AS_ACTION_ALWAYS\" without \"SHOW_AS_ACTION_IF_ROOM\" is usually a deviation from the style guide)");
            }
        }
    }
}