package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReference;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements ResourceXmlDetector, Detector.UastScanner {

    private static final String KEY_XML_ALWAYS_COUNT = "alwaysCount";
    private static final String KEY_XML_IFROOM_COUNT = "ifRoomCount";
    private static final String KEY_XML_ALWAYS_ATTRS = "alwaysAttrs";
    private static final String KEY_PROJ_ALWAYS_COUNT = "projAlwaysCount";
    private static final String KEY_PROJ_IFROOM_COUNT = "projIfRoomCount";
    private static final String KEY_PROJ_ALWAYS_LOC = "projAlwaysLoc";

    public static final Issue ISSUE = Issue.create(
        "AlwaysShowAction",
        "Usage of showAsAction=always",
        "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
        "Java code is usually a deviation from the user interface style guide. Use `ifRoom` or " +
        "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
        "If `always` is used sparingly there are usually no problems and behavior is " +
        "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
        "items. Using it more than twice in the same menu is a bad idea.\n\n" +
        "This check looks for menu XML files that contain more than two `always` " +
        "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
        "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
        "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
        Category.USABILITY, 4, Severity.WARNING,
        new Implementation(AlwaysShowActionDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) return;

        boolean hasAlways = false;
        boolean hasIfRoom = false;
        String[] parts = value.split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            if ("always".equals(trimmed)) hasAlways = true;
            if ("ifRoom".equals(trimmed)) hasIfRoom = true;
        }

        if (hasAlways) {
            Integer count = context.getClientData(KEY_XML_ALWAYS_COUNT);
            context.putClientData(KEY_XML_ALWAYS_COUNT, count == null ? 1 : count + 1);
            List<Attr> attrs = context.getClientData(KEY_XML_ALWAYS_ATTRS);
            if (attrs == null) {
                attrs = new ArrayList<>();
                context.putClientData(KEY_XML_ALWAYS_ATTRS, attrs);
            }
            attrs.add(attribute);
        }
        if (hasIfRoom) {
            Integer count = context.getClientData(KEY_XML_IFROOM_COUNT);
            context.putClientData(KEY_XML_IFROOM_COUNT, count == null ? 1 : count + 1);
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        Integer alwaysCount = context.getClientData(KEY_XML_ALWAYS_COUNT);
        if (alwaysCount == null) return;

        Integer ifRoomCount = context.getClientData(KEY_XML_IFROOM_COUNT);
        int always = alwaysCount;
        int ifRoom = ifRoomCount == null ? 0 : ifRoomCount;

        if (always > 2 || (always > 0 && ifRoom == 0)) {
            List<Attr> attrs = context.getClientData(KEY_XML_ALWAYS_ATTRS);
            if (attrs != null) {
                for (Attr attr : attrs) {
                    context.report(ISSUE, attr, "Use \"ifRoom\" instead of \"always\"");
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReference.class);
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReference reference) {
        PsiElement resolved = reference.resolve();
        if (!(resolved instanceof PsiField)) return;

        PsiField field = (PsiField) resolved;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) return;

        String qualifiedName = containingClass.getQualifiedName();
        if (!"android.view.MenuItem".equals(qualifiedName)) return;

        String name = field.getName();
        Project project = context.getProject();

        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            Integer count = project.getClientData(KEY_PROJ_ALWAYS_COUNT);
            project.putClientData(KEY_PROJ_ALWAYS_COUNT, count == null ? 1 : count + 1);
            if (count == null) {
                project.putClientData(KEY_PROJ_ALWAYS_LOC, context.getLocation(reference));
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            Integer count = project.getClientData(KEY_PROJ_IFROOM_COUNT);
            project.putClientData(KEY_PROJ_IFROOM_COUNT, count == null ? 1 : count + 1);
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        Project project = context.getProject();
        Integer alwaysCount = project.getClientData(KEY_PROJ_ALWAYS_COUNT);
        if (alwaysCount == null || alwaysCount == 0) return;

        Integer ifRoomCount = project.getClientData(KEY_PROJ_IFROOM_COUNT);
        int ifRoom = ifRoomCount == null ? 0 : ifRoomCount;

        if (ifRoom == 0) {
            Location location = project.getClientData(KEY_PROJ_ALWAYS_LOC);
            if (location == null) {
                location = Location.create(project.getDir());
            }
            context.report(ISSUE, location,
                "Project uses SHOW_AS_ACTION_ALWAYS but never SHOW_AS_ACTION_IF_ROOM; consider using IF_ROOM instead");
        }
    }
}