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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
            "Java code is usually a deviation from the user interface style guide. Use `ifRoom` or " +
            "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
            "If `always` is used sparingly there are usually no problems and behavior is roughly " +
            "equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more " +
            "than twice in the same menu is a bad idea.\n\n" +
            "This check looks for menu XML files that contain more than two `always` actions, or " +
            "some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that " +
            "contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to " +
            "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            4,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class, EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)));

    private int fileAlwaysCount = 0;
    private int fileIfRoomCount = 0;
    private Location firstAlwaysLocation = null;

    private int projectAlwaysCount = 0;
    private int projectIfRoomCount = 0;

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(Context context) {
        fileAlwaysCount = 0;
        fileIfRoomCount = 0;
        firstAlwaysLocation = null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value.contains("always")) {
            fileAlwaysCount++;
            if (firstAlwaysLocation == null) {
                firstAlwaysLocation = context.getLocation(attribute);
            }
        }
        if (value.contains("ifRoom")) {
            fileIfRoomCount++;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (fileAlwaysCount > 0 && (fileAlwaysCount > 2 || fileIfRoomCount == 0)) {
                String message = "Use `ifRoom` instead of `always` for showAsAction";
                Location location = firstAlwaysLocation != null ? firstAlwaysLocation : xmlContext.getLocation(xmlContext.document.getDocumentElement());
                xmlContext.report(ISSUE, location, message);
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(JavaContext context, UReferenceExpression reference, PsiElement resolved) {
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    projectAlwaysCount++;
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    projectIfRoomCount++;
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (projectAlwaysCount > 0 && projectIfRoomCount == 0) {
            String message = "Use `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead of `MenuItem.SHOW_AS_ACTION_ALWAYS`";
            context.report(ISSUE, Location.create(context.getProject().getDir()), message);
        }
    }
}