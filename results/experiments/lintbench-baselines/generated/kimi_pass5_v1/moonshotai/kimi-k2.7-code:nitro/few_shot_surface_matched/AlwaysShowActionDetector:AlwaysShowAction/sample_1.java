package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;

public class AlwaysShowActionDetector extends ResourceXmlDetector
        implements SourceCodeScanner, XmlScanner {

    private static final String SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";
    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of showAsAction=always",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS`"
                            + " in Java code is usually a deviation from the user interface style"
                            + " guide. Use `ifRoom` or `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private int mAlwaysCount;
    private int mIfRoomCount;
    private Location mFirstAlwaysLocation;

    private boolean mJavaAlwaysSeen;
    private boolean mJavaIfRoomSeen;
    private Location mJavaAlwaysLocation;

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SHOW_AS_ACTION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
        mFirstAlwaysLocation = null;
    }

    @Override
    public void visitAttribute(
            @NonNull XmlContext context, @NonNull org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        for (String flag : value.split("\\|")) {
            String trimmed = flag.trim();
            if (VALUE_ALWAYS.equals(trimmed)) {
                mAlwaysCount++;
                if (mFirstAlwaysLocation == null) {
                    mFirstAlwaysLocation = context.getValueLocation(attribute);
                }
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                mIfRoomCount++;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext) || mAlwaysCount == 0) {
            return;
        }

        if (mAlwaysCount > 2 || mIfRoomCount == 0) {
            context.report(
                    ISSUE,
                    mFirstAlwaysLocation,
                    "Consider using `showAsAction=\"ifRoom\"` instead of `always`; using `always`"
                            + " more than twice, or without any `ifRoom` items, is not recommended");
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement resolved) {
        if (!(resolved instanceof PsiField)) {
            return;
        }

        PsiField field = (PsiField) resolved;
        if (!MENU_ITEM_CLASS.equals(getQualifiedName(field.getContainingClass()))) {
            return;
        }

        String name = field.getName();
        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            mJavaAlwaysSeen = true;
            if (mJavaAlwaysLocation == null) {
                mJavaAlwaysLocation = context.getLocation(reference);
            }
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mJavaIfRoomSeen = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mJavaAlwaysSeen && !mJavaIfRoomSeen && mJavaAlwaysLocation != null) {
            context.report(
                    ISSUE,
                    mJavaAlwaysLocation,
                    "Consider using `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead of"
                            + " `MenuItem.SHOW_AS_ACTION_ALWAYS`");
        }

        mJavaAlwaysSeen = false;
        mJavaIfRoomSeen = false;
        mJavaAlwaysLocation = null;
    }

    private static String getQualifiedName(com.intellij.psi.PsiClass psiClass) {
        return psiClass == null ? null : psiClass.getQualifiedName();
    }
}