package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "in Java code is usually a deviation from the user interface style guide. "
                            + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
                            + "\n"
                            + "If `always` is used sparingly there are usually no problems and behavior is "
                            + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                            + "items. Using it more than twice in the same menu is a bad idea.\n"
                            + "\n"
                            + "This check looks for menu XML files that contain more than two `always` "
                            + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                            + "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    private final List<Attr> mAlwaysAttributes = new ArrayList<>();
    private int mIfRoomCount = 0;

    private final List<Location> mAlwaysLocations = new ArrayList<>();
    private boolean mHasIfRoom = false;

    public AlwaysShowActionDetector() {}

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
        if (context instanceof XmlContext) {
            mAlwaysAttributes.clear();
            mIfRoomCount = 0;
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value.contains("always")) {
            mAlwaysAttributes.add(attribute);
        }
        if (value.contains("ifRoom")) {
            mIfRoomCount++;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            int alwaysCount = mAlwaysAttributes.size();
            if (alwaysCount > 2) {
                for (Attr attribute : mAlwaysAttributes) {
                    xmlContext.report(
                            ISSUE,
                            attribute,
                            xmlContext.getLocation(attribute),
                            "Prefer \"`ifRoom`\" instead of \"`always`\"");
                }
            } else if (alwaysCount > 0 && mIfRoomCount == 0) {
                for (Attr attribute : mAlwaysAttributes) {
                    xmlContext.report(
                            ISSUE,
                            attribute,
                            xmlContext.getLocation(attribute),
                            "Prefer \"`ifRoom`\" instead of \"`always`\"");
                }
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(
            JavaContext context,
            UReferenceExpression reference,
            PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            if (field.getContainingClass() != null
                    && "android.view.MenuItem".equals(field.getContainingClass().getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    mAlwaysLocations.add(context.getLocation(reference));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    mHasIfRoom = true;
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (!mAlwaysLocations.isEmpty() && !mHasIfRoom) {
            for (Location location : mAlwaysLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Prefer \"`SHOW_AS_ACTION_IF_ROOM`\" instead of \"`SHOW_AS_ACTION_ALWAYS`\"");
            }
        }
    }
}