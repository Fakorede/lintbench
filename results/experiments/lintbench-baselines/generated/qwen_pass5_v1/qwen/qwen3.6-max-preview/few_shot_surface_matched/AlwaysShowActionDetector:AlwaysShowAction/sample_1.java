package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiReference;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                    + "Java code is usually a deviation from the user interface style guide. Use `ifRoom` or "
                    + "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior is "
                    + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                    + "items. Using it more than twice in the same menu is a bad idea.\n\n"
                    + "This check looks for menu XML files that contain more than two `always` "
                    + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                    + "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                    + "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private int xmlAlwaysCount;
    private int xmlIfRoomCount;
    private Attr firstXmlAlwaysAttr;

    private int javaAlwaysCount;
    private int javaIfRoomCount;
    private UReferenceExpression firstJavaAlwaysRef;
    private JavaContext javaContext;

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        xmlAlwaysCount = 0;
        xmlIfRoomCount = 0;
        firstXmlAlwaysAttr = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            if (xmlAlwaysCount > 2 || (xmlAlwaysCount > 0 && xmlIfRoomCount == 0)) {
                if (firstXmlAlwaysAttr != null) {
                    XmlContext xmlContext = (XmlContext) context;
                    xmlContext.report(ISSUE, firstXmlAlwaysAttr, xmlContext.getLocation(firstXmlAlwaysAttr),
                            "Prefer \"ifRoom\" instead of \"always\"");
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (javaAlwaysCount > 0 && javaIfRoomCount == 0 && firstJavaAlwaysRef != null && javaContext != null) {
            javaContext.report(ISSUE, firstJavaAlwaysRef, javaContext.getLocation(firstJavaAlwaysRef),
                    "Prefer SHOW_AS_ACTION_IF_ROOM instead of SHOW_AS_ACTION_ALWAYS");
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.contains("always")) {
            xmlAlwaysCount++;
            if (firstXmlAlwaysAttr == null) {
                firstXmlAlwaysAttr = attribute;
            }
        }
        if (value.contains("ifRoom")) {
            xmlIfRoomCount++;
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference, @NonNull PsiReference resolved) {
        String name = reference.getReferenceName();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            javaAlwaysCount++;
            if (firstJavaAlwaysRef == null) {
                firstJavaAlwaysRef = reference;
                javaContext = context;
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            javaIfRoomCount++;
        }
    }
}