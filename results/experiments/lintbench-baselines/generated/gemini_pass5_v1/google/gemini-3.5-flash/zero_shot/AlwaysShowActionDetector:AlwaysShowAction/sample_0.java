package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Attr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
            "Java code is usually a deviation from the user interface style guide. Use `ifRoom` " +
            "or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n" +
            "\n" +
            "If `always` is used sparingly there are usually no problems and behavior is " +
            "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
            "items. Using it more than twice in the same menu is a bad idea.\n" +
            "\n" +
            "This check looks for menu XML files that contain more than two `always` " +
            "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
            "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
            "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE))
    );

    private int mAlwaysCount;
    private int mIfRoomCount;
    private final List<Attr> mAlwaysAttributes = new ArrayList<>();

    private boolean mHasAlways;
    private boolean mHasIfRoom;
    private final List<Location> mAlwaysJavaLocations = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
        mAlwaysAttributes.clear();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null) {
            if (value.contains("always")) {
                mAlwaysCount++;
                mAlwaysAttributes.add(attribute);
            }
            if (value.contains("ifRoom")) {
                mIfRoomCount++;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mAlwaysCount > 2) {
            for (Attr attr : mAlwaysAttributes) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Prefer \"ifRoom\" instead of \"always\"; \"always\" should only be used " +
                        "sparingly (and this file uses it " + mAlwaysCount + " times)");
            }
        } else if (mAlwaysCount > 0 && mIfRoomCount == 0) {
            for (Attr attr : mAlwaysAttributes) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Prefer \"ifRoom\" instead of \"always\"; \"always\" should only be used " +
                        "sparingly and in combination with \"ifRoom\"");
            }
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mHasAlways = false;
        mHasIfRoom = false;
        mAlwaysJavaLocations.clear();
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return java.util.Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference, @NonNull PsiElement resolved) {
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    mHasAlways = true;
                    mAlwaysJavaLocations.add(context.getLocation(reference));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    mHasIfRoom = true;
                }
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mHasAlways && !mHasIfRoom) {
            for (Location location : mAlwaysJavaLocations) {
                context.report(ISSUE, location,
                        "Prefer \"SHOW_AS_ACTION_IF_ROOM\" instead of \"SHOW_AS_ACTION_ALWAYS\"");
            }
        }
    }
}