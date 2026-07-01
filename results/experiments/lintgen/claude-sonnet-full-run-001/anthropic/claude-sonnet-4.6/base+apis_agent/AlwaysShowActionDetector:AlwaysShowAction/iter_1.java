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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
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
            6,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.ALL_JAVA_FILES)));

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";

    // Per-project tracking for Java/Kotlin source
    private final List<Location> mAlwaysLocations = new ArrayList<>();
    private boolean mHasIfRoom = false;

    // Per-XML-file state
    private int mXmlAlwaysCount;
    private int mXmlIfRoomCount;
    private final List<Location> mXmlAlwaysLocations = new ArrayList<>();

    // -----------------------------------------------------------------------
    // XmlScanner
    // -----------------------------------------------------------------------

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_SHOW_AS_ACTION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mXmlAlwaysCount = 0;
        mXmlIfRoomCount = 0;
        mXmlAlwaysLocations.clear();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
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
            mXmlAlwaysCount++;
            mXmlAlwaysLocations.add(context.getLocation(attribute));
        }
        if (hasIfRoom) {
            mXmlIfRoomCount++;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        if (mXmlAlwaysCount > 2 || (mXmlAlwaysCount > 0 && mXmlIfRoomCount == 0)) {
            String message = "Prefer `ifRoom` over `always`; reserve the `always` option for at " +
                    "most 1 or 2 actions";
            for (Location location : mXmlAlwaysLocations) {
                xmlContext.report(ISSUE, location, message);
            }
        }
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner
    // -----------------------------------------------------------------------

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement referenced) {
        if (!(referenced instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) referenced;
        String className = field.getContainingClass() != null
                ? field.getContainingClass().getQualifiedName()
                : null;
        if (!MENU_ITEM_CLASS.equals(className)) {
            return;
        }
        String name = field.getName();
        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            mAlwaysLocations.add(context.getLocation((UElement) reference));
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mAlwaysLocations.isEmpty() && !mHasIfRoom) {
            String message = "Prefer `SHOW_AS_ACTION_IF_ROOM` over `SHOW_AS_ACTION_ALWAYS`";
            for (Location location : mAlwaysLocations) {
                context.report(ISSUE, location, message);
            }
        }
        mAlwaysLocations.clear();
        mHasIfRoom = false;
    }
}