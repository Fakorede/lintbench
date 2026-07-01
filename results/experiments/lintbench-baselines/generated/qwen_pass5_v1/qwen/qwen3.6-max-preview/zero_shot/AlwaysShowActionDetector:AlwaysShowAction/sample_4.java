package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {
    public static final Issue ISSUE = Issue.create(
        "AlwaysShowAction",
        "Usage of showAsAction=always",
        "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
        "If `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.\n\n" +
        "This check looks for menu XML files that contain more than two `always` actions, or some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
        Category.USABILITY,
        4,
        Severity.WARNING,
        new Implementation(AlwaysShowActionDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";
    private static final String ALWAYS_FIELD = "SHOW_AS_ACTION_ALWAYS";
    private static final String IF_ROOM_FIELD = "SHOW_AS_ACTION_IF_ROOM";

    private int alwaysCount;
    private int ifRoomCount;
    private List<Location> alwaysLocations;

    @Override
    public void beforeCheckFile(Context context) {
        alwaysCount = 0;
        ifRoomCount = 0;
        alwaysLocations = new ArrayList<>();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String value = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_SHOW_AS_ACTION);
        if (value == null || value.isEmpty()) {
            value = element.getAttributeNS(SdkConstants.AUTO_URI, ATTR_SHOW_AS_ACTION);
        }
        if (value != null) {
            if (value.contains(VALUE_ALWAYS)) {
                alwaysCount++;
                alwaysLocations.add(context.getLocation(element));
            } else if (value.contains(VALUE_IF_ROOM)) {
                ifRoomCount++;
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(ALWAYS_FIELD, IF_ROOM_FIELD);
    }

    @Override
    public void visitReference(JavaContext context, UReferenceExpression reference) {
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && MENU_ITEM_CLASS.equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if (ALWAYS_FIELD.equals(name)) {
                    alwaysCount++;
                    alwaysLocations.add(context.getLocation(reference));
                } else if (IF_ROOM_FIELD.equals(name)) {
                    ifRoomCount++;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        boolean report = false;
        if (context instanceof XmlContext) {
            if (alwaysCount > 2 || (alwaysCount > 0 && ifRoomCount == 0)) {
                report = true;
            }
        } else if (context instanceof JavaContext) {
            if (alwaysCount > 0 && ifRoomCount == 0) {
                report = true;
            }
        }

        if (report && !alwaysLocations.isEmpty()) {
            String message = context instanceof XmlContext
                ? "Prefer `ifRoom` instead of `always`"
                : "Prefer `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`";
            for (Location location : alwaysLocations) {
                context.report(ISSUE, location, message);
            }
        }
    }
}