package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_SHOW_AS_ACTION;

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
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlwaysShowActionDetector extends Detector
        implements Detector.XmlScanner, Detector.UastScanner {

    private static final String CLASS_MENU_ITEM = "android.view.MenuItem";
    private static final String FIELD_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String FIELD_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final Implementation IMPLEMENTATION = new Implementation(
            AlwaysShowActionDetector.class,
            Scope.JAVA_FILE_SCOPE,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.\n\n"
                    + "This check looks for menu XML files that contain more than two `always` actions, or some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            IMPLEMENTATION);

    private final List<Attr> mAlwaysAttributes = new ArrayList<>();
    private int mIfRoomCount;

    private final List<Location> mAlwaysJavaLocations = new ArrayList<>();
    private boolean mIfRoomJavaSeen;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysAttributes.clear();
        mIfRoomCount = 0;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mAlwaysJavaLocations.clear();
        mIfRoomJavaSeen = false;
    }

    // ---- XML ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_SHOW_AS_ACTION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        boolean hasAlways = false;
        boolean hasIfRoom = false;
        for (String token : value.split("\\|")) {
            String trimmed = token.trim();
            if (VALUE_ALWAYS.equals(trimmed)) {
                hasAlways = true;
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                hasIfRoom = true;
            }
        }

        if (hasAlways) {
            mAlwaysAttributes.add(attribute);
        }
        if (hasIfRoom) {
            mIfRoomCount++;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysAttributes.isEmpty() || !(context instanceof XmlContext)) {
            return;
        }

        int alwaysCount = mAlwaysAttributes.size();
        if (alwaysCount > 2 || mIfRoomCount == 0) {
            String message = alwaysCount > 2
                    ? String.format(
                            "Menu has %1$d `showAsAction=\"always\"` items; using more than two is not recommended",
                            alwaysCount)
                    : "Menu contains `showAsAction=\"always\"` but no `ifRoom` items";

            XmlContext xmlContext = (XmlContext) context;
            for (Attr attribute : mAlwaysAttributes) {
                xmlContext.report(
                        ISSUE,
                        attribute,
                        xmlContext.getLocation(attribute),
                        message);
            }
        }
    }

    // ---- Java/Kotlin ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                if (!(node.resolve() instanceof PsiField)) {
                    return;
                }

                PsiField field = (PsiField) node.resolve();
                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null
                        || !CLASS_MENU_ITEM.equals(containingClass.getQualifiedName())) {
                    return;
                }

                String name = field.getName();
                if (FIELD_ALWAYS.equals(name)) {
                    mAlwaysJavaLocations.add(context.getLocation(node));
                } else if (FIELD_IF_ROOM.equals(name)) {
                    mIfRoomJavaSeen = true;
                }
            }
        };
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mAlwaysJavaLocations.isEmpty() || mIfRoomJavaSeen) {
            return;
        }

        String message =
                "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` is not recommended; use `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead";
        for (Location location : mAlwaysJavaLocations) {
            context.report(ISSUE, location, message);
        }
    }
}