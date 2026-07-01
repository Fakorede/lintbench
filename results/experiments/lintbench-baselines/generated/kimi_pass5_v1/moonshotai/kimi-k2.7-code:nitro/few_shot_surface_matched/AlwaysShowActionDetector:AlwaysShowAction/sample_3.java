package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

import org.jetbrains.uast.UElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlwaysShowActionDetector extends ResourceXmlDetector
        implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Always Show Action",
                    "Using `showAsAction=\"always\"` in menu XML, or "
                            + "`MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a "
                            + "deviation from the user interface style guide. Use `ifRoom` or "
                            + "`MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            Scope.RESOURCE_XML_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private int mAlwaysCount;
    private int mIfRoomCount;

    private final List<Location> mAlwaysLocations = new ArrayList<>();
    private boolean mSawIfRoom;

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        for (String flag : value.split("\\|")) {
            String trimmed = flag.trim();
            if ("always".equals(trimmed)) {
                mAlwaysCount++;
            } else if ("ifRoom".equals(trimmed)) {
                mIfRoomCount++;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        if (mAlwaysCount > 2 || (mAlwaysCount > 0 && mIfRoomCount == 0)) {
            org.w3c.dom.Element root = xmlContext.document.getDocumentElement();
            Location location =
                    root != null
                            ? xmlContext.getLocation(root)
                            : xmlContext.getLocation();
            context.report(
                    ISSUE,
                    location,
                    "Avoid using `showAsAction=\"always\"`; prefer `ifRoom`");
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(JavaContext context, UElement node, PsiElement resolved) {
        if (!(resolved instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) resolved;
        String name = field.getName();
        com.intellij.psi.PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }
        if (!"android.view.MenuItem".equals(containingClass.getQualifiedName())) {
            return;
        }
        if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mSawIfRoom = true;
        } else if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mAlwaysLocations.add(context.getLocation(node));
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (!mSawIfRoom && !mAlwaysLocations.isEmpty()) {
            String message =
                    "Avoid using `MenuItem.SHOW_AS_ACTION_ALWAYS`; prefer "
                            + "`MenuItem.SHOW_AS_ACTION_IF_ROOM`";
            for (Location location : mAlwaysLocations) {
                context.report(ISSUE, location, message);
            }
        }
        mAlwaysLocations.clear();
        mSawIfRoom = false;
    }
}