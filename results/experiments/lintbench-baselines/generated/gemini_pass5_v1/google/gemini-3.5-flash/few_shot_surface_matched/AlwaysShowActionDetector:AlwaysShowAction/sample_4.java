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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "in Java code is usually a deviation from the user interface style guide. "
                            + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                            + "If `always` is used sparingly there are usually no problems and behavior is "
                            + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. "
                            + "Using it more than twice in the same menu is a bad idea.\n\n"
                            + "This check looks for menu XML files that contain more than two `always` actions, "
                            + "or some `always` actions and no `ifRoom` actions. In Java code, it looks for "
                            + "projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no "
                            + "references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES));

    private final List<Attr> mAlwaysAttributes = new ArrayList<>();
    private int mAlwaysCount = 0;
    private int mIfRoomCount = 0;

    private boolean mHasAlwaysReference = false;
    private boolean mHasIfRoomReference = false;
    private final List<LocationAndContext> mAlwaysReferences = new ArrayList<>();

    private static class LocationAndContext {
        final JavaContext context;
        final Location location;

        LocationAndContext(JavaContext context, Location location) {
            this.context = context;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            mAlwaysAttributes.clear();
            mAlwaysCount = 0;
            mIfRoomCount = 0;
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null) {
            for (String part : value.split("\\|")) {
                part = part.trim();
                if ("always".equals(part)) {
                    mAlwaysAttributes.add(attribute);
                    mAlwaysCount++;
                } else if ("ifRoom".equals(part)) {
                    mIfRoomCount++;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (mAlwaysCount > 2) {
                for (Attr attribute : mAlwaysAttributes) {
                    xmlContext.report(
                            ISSUE,
                            attribute,
                            xmlContext.getLocation(attribute),
                            "Prefer \"ifRoom\" instead of \"always\" (referencing \"always\" more than twice in the same menu is a bad idea)");
                }
            } else if (mAlwaysCount > 0 && mIfRoomCount == 0) {
                for (Attr attribute : mAlwaysAttributes) {
                    xmlContext.report(
                            ISSUE,
                            attribute,
                            xmlContext.getLocation(attribute),
                            "Prefer \"ifRoom\" instead of \"always\"");
                }
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    mHasAlwaysReference = true;
                    mAlwaysReferences.add(new LocationAndContext(context, context.getLocation(reference)));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    mHasIfRoomReference = true;
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasAlwaysReference && !mHasIfRoomReference) {
            for (LocationAndContext lac : mAlwaysReferences) {
                lac.context.report(
                        ISSUE,
                        lac.location,
                        "Prefer \"SHOW_AS_ACTION_IF_ROOM\" instead of \"SHOW_AS_ACTION_ALWAYS\"");
            }
        }
        mAlwaysReferences.clear();
        mHasAlwaysReference = false;
        mHasIfRoomReference = false;
    }
}