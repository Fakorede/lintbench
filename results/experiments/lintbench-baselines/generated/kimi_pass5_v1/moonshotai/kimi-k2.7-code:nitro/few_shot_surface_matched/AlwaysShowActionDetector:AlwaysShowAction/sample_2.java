package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
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
                    "Always Show Action",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS`"
                            + " in Java code is usually a deviation from the user interface style"
                            + " guide. Use `ifRoom` or `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    private static final String SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";

    private int mAlwaysCount;
    private int mIfRoomCount;
    private final List<Location> mAlwaysLocations = new ArrayList<>();

    private boolean mJavaAlwaysSeen;
    private boolean mJavaIfRoomSeen;
    private Location mJavaAlwaysLocation;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SHOW_AS_ACTION);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
        mAlwaysLocations.clear();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        for (String token : value.split("\\|")) {
            String trimmed = token.trim();
            if (VALUE_ALWAYS.equals(trimmed)) {
                mAlwaysCount++;
                mAlwaysLocations.add(context.getLocation(attribute));
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                mIfRoomCount++;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mAlwaysCount > 2) {
            for (Location location : mAlwaysLocations) {
                context.report(
                        ISSUE,
                        location,
                        "More than two `showAsAction=\"always\"` items in the same menu; prefer"
                                + " `ifRoom`");
            }
        } else if (mAlwaysCount > 0 && mIfRoomCount == 0) {
            for (Location location : mAlwaysLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Using `showAsAction=\"always\"` with no `showAsAction=\"ifRoom\"`; prefer"
                                + " `ifRoom`");
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
        String name = reference.getReferenceName();
        if (name == null || !isMenuItemField(referenced)) {
            return;
        }
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mJavaAlwaysSeen = true;
            mJavaAlwaysLocation = context.getLocation(reference);
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mJavaIfRoomSeen = true;
        }
    }

    private static boolean isMenuItemField(@NonNull PsiElement element) {
        if (!(element instanceof PsiField)) {
            return false;
        }
        PsiClass containingClass = ((PsiField) element).getContainingClass();
        return containingClass != null
                && MENU_ITEM_CLASS.equals(containingClass.getQualifiedName());
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mJavaAlwaysSeen && !mJavaIfRoomSeen) {
            context.report(
                    ISSUE,
                    mJavaAlwaysLocation,
                    "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` with no"
                            + " `MenuItem.SHOW_AS_ACTION_IF_ROOM`; prefer"
                            + " `MenuItem.SHOW_AS_ACTION_IF_ROOM`");
        }
        mJavaAlwaysSeen = false;
        mJavaIfRoomSeen = false;
        mJavaAlwaysLocation = null;
    }
}