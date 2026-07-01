package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector
        implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Prefer `ifRoom` instead of `always` for showAsAction",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS`"
                            + " in Java code is usually a deviation from the user interface style"
                            + " guide. Use `ifRoom` or the corresponding"
                            + " `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                            + "If `always` is used sparingly there are usually no problems and"
                            + " behavior is roughly equivalent to `ifRoom` but with preference over"
                            + " other `ifRoom` items. Using it more than twice in the same menu is a"
                            + " bad idea.\n\n"
                            + "This check looks for menu XML files that contain more than two"
                            + " `always` actions, or some `always` actions and no `ifRoom` actions."
                            + " In Java code, it looks for projects that contain references to"
                            + " `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to"
                            + " `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            Scope.JAVA_FILE_SCOPE.and(Scope.RESOURCE_FILE_SCOPE)));

    private static final String SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";

    private int mAlwaysCount;
    private int mIfRoomCount;
    private Location mFirstAlwaysLocation;

    private boolean mHasJavaAlways;
    private boolean mHasJavaIfRoom;
    private UReferenceExpression mFirstJavaAlwaysReference;
    private JavaContext mFirstJavaAlwaysContext;

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SHOW_AS_ACTION);
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (context instanceof XmlContext) {
            mAlwaysCount = 0;
            mIfRoomCount = 0;
            mFirstAlwaysLocation = null;
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.startsWith("@")) {
            return;
        }

        boolean hasAlways = false;
        boolean hasIfRoom = false;
        for (String flag : value.split("\\|")) {
            String trimmed = flag.trim();
            if (VALUE_ALWAYS.equals(trimmed)) {
                hasAlways = true;
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                hasIfRoom = true;
            }
        }

        if (hasAlways) {
            mAlwaysCount++;
            if (mFirstAlwaysLocation == null) {
                mFirstAlwaysLocation = context.getLocation(attribute);
            }
        }
        if (hasIfRoom) {
            mIfRoomCount++;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }

        if (mAlwaysCount > 2) {
            context.report(
                    ISSUE,
                    mFirstAlwaysLocation,
                    "More than two `showAsAction=\"always\"` items found in this menu;"
                            + " prefer `ifRoom`");
        } else if (mAlwaysCount > 0 && mIfRoomCount == 0) {
            context.report(
                    ISSUE,
                    mFirstAlwaysLocation,
                    "This menu uses `showAsAction=\"always\"` but no `showAsAction=\"ifRoom\"`;"
                            + " prefer `ifRoom`");
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(
            JavaContext context, UReferenceExpression reference, PsiElement referenced) {
        if (!(referenced instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) referenced;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null
                || !MENU_ITEM_CLASS.equals(containingClass.getQualifiedName())) {
            return;
        }

        String name = field.getName();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mHasJavaAlways = true;
            if (mFirstJavaAlwaysReference == null) {
                mFirstJavaAlwaysReference = reference;
                mFirstJavaAlwaysContext = context;
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mHasJavaIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (mHasJavaAlways
                && !mHasJavaIfRoom
                && mFirstJavaAlwaysReference != null
                && mFirstJavaAlwaysContext != null) {
            mFirstJavaAlwaysContext.report(
                    ISSUE,
                    mFirstJavaAlwaysReference,
                    mFirstJavaAlwaysContext.getLocation(mFirstJavaAlwaysReference),
                    "This project references `MenuItem.SHOW_AS_ACTION_ALWAYS` but not"
                            + " `MenuItem.SHOW_AS_ACTION_IF_ROOM`; prefer `ifRoom`");
        }
    }
}