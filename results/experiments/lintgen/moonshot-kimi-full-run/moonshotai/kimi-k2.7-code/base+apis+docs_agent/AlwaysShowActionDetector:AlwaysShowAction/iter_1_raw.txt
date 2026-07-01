package com.android.tools.lint.checks;

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
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE))
    );

    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";
    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private final Map<File, MenuCounts> mMenuCounts = new HashMap<>();
    private boolean mHasAlwaysRef;
    private boolean mHasIfRoomRef;
    private Location mFirstAlwaysRefLocation;

    @Override
    @NotNull
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE);
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_SHOW_AS_ACTION);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("@")) {
            return;
        }

        MenuCounts counts = mMenuCounts.get(context.file);
        if (counts == null) {
            counts = new MenuCounts();
            mMenuCounts.put(context.file, counts);
        }

        for (String token : value.split("\\|")) {
            String trimmed = token.trim();
            if (VALUE_ALWAYS.equals(trimmed)) {
                counts.alwaysCount++;
                if (counts.firstAlwaysLocation == null) {
                    counts.firstAlwaysLocation = context.getLocation(attribute);
                }
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                counts.ifRoomCount++;
            }
        }
    }

    @Override
    @NotNull
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NotNull JavaContext context,
            @NotNull UReferenceExpression reference,
            @NotNull PsiElement referenced) {
        if (!(referenced instanceof PsiField)) {
            return;
        }

        if (!context.getEvaluator().isMemberInClass(referenced, MENU_ITEM_CLASS)) {
            return;
        }

        String name = ((PsiField) referenced).getName();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mHasAlwaysRef = true;
            if (mFirstAlwaysRefLocation == null) {
                mFirstAlwaysRefLocation = context.getLocation(reference);
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mHasIfRoomRef = true;
        }
    }

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mMenuCounts.clear();
        mHasAlwaysRef = false;
        mHasIfRoomRef = false;
        mFirstAlwaysRefLocation = null;
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        for (Map.Entry<File, MenuCounts> entry : mMenuCounts.entrySet()) {
            MenuCounts counts = entry.getValue();
            if (counts.alwaysCount > 2) {
                context.report(
                        ISSUE,
                        counts.firstAlwaysLocation,
                        "This menu has more than two `showAsAction=\"always\"` items; prefer `ifRoom` instead.");
            } else if (counts.alwaysCount > 0 && counts.ifRoomCount == 0) {
                context.report(
                        ISSUE,
                        counts.firstAlwaysLocation,
                        "This menu has `showAsAction=\"always\"` items but no `showAsAction=\"ifRoom\"` items; prefer `ifRoom` instead.");
            }
        }

        if (mHasAlwaysRef && !mHasIfRoomRef) {
            context.report(
                    ISSUE,
                    mFirstAlwaysRefLocation,
                    "This project references `MenuItem.SHOW_AS_ACTION_ALWAYS` but not `MenuItem.SHOW_AS_ACTION_IF_ROOM`; prefer `SHOW_AS_ACTION_IF_ROOM` instead.");
        }
    }

    private static class MenuCounts {
        int alwaysCount;
        int ifRoomCount;
        Location firstAlwaysLocation;
    }
}