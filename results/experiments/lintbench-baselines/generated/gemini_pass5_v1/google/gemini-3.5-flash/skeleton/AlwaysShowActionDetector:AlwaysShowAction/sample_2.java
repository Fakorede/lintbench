package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UReferenceExpression;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements Detector.SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AlwaysShowActionDetector.class, java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                            + "Java code is usually a deviation from the user interface style guide. "
                            + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
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
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mAlwaysCount;
    private int mIfRoomCount;
    private final java.util.List<org.w3c.dom.Attr> mAlwaysAttrs = new java.util.ArrayList<>();

    private boolean mHasShowAsActionAlways = false;
    private boolean mHasShowAsActionIfRoom = false;
    private final java.util.List<Location> mAlwaysReferenceLocations = new java.util.ArrayList<>();

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MENU;
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@com.android.annotations.NonNull Context context) {
        if (context instanceof XmlContext) {
            mAlwaysCount = 0;
            mIfRoomCount = 0;
            mAlwaysAttrs.clear();
        }
    }

    @Override
    public void afterCheckFile(@com.android.annotations.NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (mAlwaysCount > 2 || (mAlwaysCount > 0 && mIfRoomCount == 0)) {
                for (org.w3c.dom.Attr attribute : mAlwaysAttrs) {
                    xmlContext.report(
                            ISSUE,
                            attribute,
                            xmlContext.getLocation(attribute),
                            "Prefer \"`ifRoom`\" instead of \"`always`\"");
                }
            }
        }
    }

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        mHasShowAsActionAlways = false;
        mHasShowAsActionIfRoom = false;
        mAlwaysReferenceLocations.clear();
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        if (mHasShowAsActionAlways && !mHasShowAsActionIfRoom) {
            for (Location location : mAlwaysReferenceLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Prefer `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
            }
        }
    }

    @Override
    public void visitAttribute(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value.contains("always")) {
            mAlwaysCount++;
            mAlwaysAttrs.add(attribute);
        }
        if (value.contains("ifRoom")) {
            mIfRoomCount++;
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
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            String name = field.getName();
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    mHasShowAsActionAlways = true;
                    mAlwaysReferenceLocations.add(context.getLocation(reference));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    mHasShowAsActionIfRoom = true;
                }
            }
        }
    }
}