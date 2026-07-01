package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_SHOW_AS_ACTION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaPsiScanner;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, JavaPsiScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                    + "Java code is usually a deviation from the user interface style guide. Use "
                    + "`ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior is "
                    + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                    + "items. Using it more than twice in the same menu is a bad idea.\n\n"
                    + "This check looks for menu XML files that contain more than two `always` "
                    + "actions, or some `always` actions and no `ifRoom` actions. In Java code, it "
                    + "looks for projects that contain references to "
                    + "`MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to "
                    + "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
    );

    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";
    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private int mXmlAlwaysCount;
    private int mXmlIfRoomCount;
    private Location mXmlFirstAlwaysLocation;

    private int mJavaAlwaysCount;
    private int mJavaIfRoomCount;
    private Location mJavaFirstAlwaysLocation;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_SHOW_AS_ACTION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        if (containsValue(value, VALUE_ALWAYS)) {
            mXmlAlwaysCount++;
            if (mXmlFirstAlwaysLocation == null) {
                mXmlFirstAlwaysLocation = context.getLocation(attribute);
            }
        }
        if (containsValue(value, VALUE_IF_ROOM)) {
            mXmlIfRoomCount++;
        }
    }

    @Nullable
    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.singletonList(PsiReferenceExpression.class);
    }

    @Override
    public void visitPsiElement(@NonNull JavaContext context, @NonNull PsiElement node) {
        PsiReferenceExpression expression = (PsiReferenceExpression) node;
        PsiElement resolved = expression.resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }

        PsiField field = (PsiField) resolved;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }

        if (!MENU_ITEM_CLASS.equals(containingClass.getQualifiedName())) {
            return;
        }

        String name = field.getName();
        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            mJavaAlwaysCount++;
            if (mJavaFirstAlwaysLocation == null) {
                mJavaFirstAlwaysLocation = context.getLocation(node);
            }
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mJavaIfRoomCount++;
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlContext.getResourceFolderType() == ResourceFolderType.MENU) {
                mXmlAlwaysCount = 0;
                mXmlIfRoomCount = 0;
                mXmlFirstAlwaysLocation = null;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        if (xmlContext.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }

        if (mXmlAlwaysCount > 2) {
            xmlContext.report(
                    ISSUE,
                    mXmlFirstAlwaysLocation,
                    "More than two `showAsAction=\"always\"` items found in this menu; "
                            + "use `showAsAction=\"ifRoom\"` instead");
        } else if (mXmlAlwaysCount > 0 && mXmlIfRoomCount == 0) {
            xmlContext.report(
                    ISSUE,
                    mXmlFirstAlwaysLocation,
                    "This menu contains `showAsAction=\"always\"` but no "
                            + "`showAsAction=\"ifRoom\"`; use `ifRoom` instead");
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mJavaAlwaysCount = 0;
        mJavaIfRoomCount = 0;
        mJavaFirstAlwaysLocation = null;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mJavaAlwaysCount > 0 && mJavaIfRoomCount == 0 && mJavaFirstAlwaysLocation != null) {
            context.report(
                    ISSUE,
                    mJavaFirstAlwaysLocation,
                    "This project contains references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "but no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`");
        }
    }

    private static boolean containsValue(@NonNull String value, @NonNull String target) {
        for (String part : value.split("\\|")) {
            if (target.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }
}