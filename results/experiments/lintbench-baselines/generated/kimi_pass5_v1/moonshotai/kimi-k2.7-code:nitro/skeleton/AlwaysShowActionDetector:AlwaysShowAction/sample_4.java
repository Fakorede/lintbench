package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements Detector.JavaPsiScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.<br/><br/>If `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.<br/><br/>This check looks for menu XML files that contain more than two `always` actions, or some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mAlwaysCount;
    private int mIfRoomCount;

    private boolean mHasJavaAlways;
    private boolean mHasJavaIfRoom;
    private JavaContext mFirstAlwaysContext;
    private UReferenceExpression mFirstAlwaysReference;

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
        if (context instanceof XmlContext) {
            mAlwaysCount = 0;
            mIfRoomCount = 0;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        if (mAlwaysCount > 2) {
            xmlContext.report(
                    ISSUE,
                    xmlContext.getLocation(xmlContext.document.getDocumentElement()),
                    "More than two `showAsAction=\"always\"` items in this menu; consider using `ifRoom`");
        } else if (mAlwaysCount > 0 && mIfRoomCount == 0) {
            xmlContext.report(
                    ISSUE,
                    xmlContext.getLocation(xmlContext.document.getDocumentElement()),
                    "Found `showAsAction=\"always\"` but no `showAsAction=\"ifRoom\"`; consider using `ifRoom`");
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasJavaAlways = false;
        mHasJavaIfRoom = false;
        mFirstAlwaysContext = null;
        mFirstAlwaysReference = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasJavaAlways && !mHasJavaIfRoom && mFirstAlwaysContext != null) {
            mFirstAlwaysContext.report(
                    ISSUE,
                    mFirstAlwaysContext.getLocation(mFirstAlwaysReference),
                    "Using `MenuItem.SHOW_AS_ACTION_ALWAYS`; consider using `MenuItem.SHOW_AS_ACTION_IF_ROOM`");
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        for (String flag : value.split("\\|")) {
            flag = flag.trim();
            if ("always".equals(flag)) {
                mAlwaysCount++;
            } else if ("ifRoom".equals(flag)) {
                mIfRoomCount++;
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement referenced) {
        if (isMenuItemConstant(referenced, "SHOW_AS_ACTION_ALWAYS")) {
            if (!mHasJavaAlways) {
                mFirstAlwaysContext = context;
                mFirstAlwaysReference = reference;
            }
            mHasJavaAlways = true;
        } else if (isMenuItemConstant(referenced, "SHOW_AS_ACTION_IF_ROOM")) {
            mHasJavaIfRoom = true;
        }
    }

    private static boolean isMenuItemConstant(@NonNull PsiElement referenced, @NonNull String name) {
        if (!(referenced instanceof PsiField)) {
            return false;
        }
        PsiField field = (PsiField) referenced;
        if (!name.equals(field.getName())) {
            return false;
        }
        PsiClass containingClass = field.getContainingClass();
        return containingClass != null
                && "android.view.MenuItem".equals(containingClass.getQualifiedName());
    }
}