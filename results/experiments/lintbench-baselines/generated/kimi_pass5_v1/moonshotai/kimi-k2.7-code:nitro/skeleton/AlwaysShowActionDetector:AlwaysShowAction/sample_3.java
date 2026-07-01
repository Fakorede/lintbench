package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AlwaysShowActionDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead. If `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mAlwaysCount;
    private int mIfRoomCount;
    private Attr mLastAlwaysAttribute;

    private boolean mHasJavaAlways;
    private boolean mHasJavaIfRoom;
    private Location mJavaAlwaysLocation;

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
        mLastAlwaysAttribute = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null) {
            if (value.contains("always")) {
                mAlwaysCount++;
                mLastAlwaysAttribute = attribute;
            }
            if (value.contains("ifRoom")) {
                mIfRoomCount++;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysCount > 2 || (mAlwaysCount > 0 && mIfRoomCount == 0)) {
            XmlContext xmlContext = (XmlContext) context;
            Location location = mLastAlwaysAttribute != null
                    ? xmlContext.getLocation(mLastAlwaysAttribute)
                    : xmlContext.getLocation(xmlContext.getDocument().getDocumentElement());
            xmlContext.report(
                    ISSUE,
                    location,
                    "Avoid using `showAsAction=\"always\"` in menus; prefer `showAsAction=\"ifRoom\"`.");
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
        String name = reference.getName();
        if (name == null) {
            return;
        }

        PsiField field = referenced instanceof PsiField ? (PsiField) referenced : null;
        if (field == null || field.getContainingClass() == null) {
            return;
        }

        String className = field.getContainingClass().getQualifiedName();
        if (!"android.view.MenuItem".equals(className)) {
            return;
        }

        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mHasJavaAlways = true;
            mJavaAlwaysLocation = context.getLocation(reference);
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mHasJavaIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasJavaAlways && !mHasJavaIfRoom && mJavaAlwaysLocation != null) {
            context.report(
                    ISSUE,
                    mJavaAlwaysLocation,
                    "Avoid using `MenuItem.SHOW_AS_ACTION_ALWAYS`; prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM`.");
        }
    }
}