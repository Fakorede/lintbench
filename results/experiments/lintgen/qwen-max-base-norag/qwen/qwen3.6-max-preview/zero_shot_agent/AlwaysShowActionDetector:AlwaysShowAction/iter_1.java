package com.android.tools.lint.checks;

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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String APP_URI = "http://schemas.android.com/apk/res-auto";
    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String TAG_ITEM = "item";

    private static final String ALWAYS = "always";
    private static final String IF_ROOM = "ifRoom";

    private int xmlAlwaysCount;
    private int xmlIfRoomCount;
    private Location xmlReportLocation;

    private int javaAlwaysCount;
    private int javaIfRoomCount;
    private Location javaReportLocation;

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
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class, Scope.JAVA_AND_RESOURCE_FILES)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ITEM);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SHOW_AS_ACTION);
        if (attr == null) {
            attr = element.getAttributeNodeNS(APP_URI, ATTR_SHOW_AS_ACTION);
        }
        if (attr == null) {
            return;
        }

        String value = attr.getValue();
        if (value != null) {
            if (value.contains(ALWAYS)) {
                xmlAlwaysCount++;
                if (xmlReportLocation == null) {
                    xmlReportLocation = context.getLocation(attr);
                }
            }
            if (value.contains(IF_ROOM)) {
                xmlIfRoomCount++;
            }
        }
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (context instanceof XmlContext) {
            xmlAlwaysCount = 0;
            xmlIfRoomCount = 0;
            xmlReportLocation = null;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof XmlContext) {
            if (xmlAlwaysCount > 2 || (xmlAlwaysCount > 0 && xmlIfRoomCount == 0)) {
                Location location = xmlReportLocation != null ? xmlReportLocation : Location.create(context.file);
                context.report(ISSUE, location, "Prefer `ifRoom` over `always` for `showAsAction`");
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    public void visitReferenceExpression(JavaContext context, UReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    javaAlwaysCount++;
                    if (javaReportLocation == null) {
                        javaReportLocation = context.getLocation(node);
                    }
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    javaIfRoomCount++;
                }
            }
        }
    }

    @Override
    public void beforeCheckProject(Context context) {
        javaAlwaysCount = 0;
        javaIfRoomCount = 0;
        javaReportLocation = null;
    }

    @Override
    public void afterCheckProject(Context context) {
        if (javaAlwaysCount > 0 && javaIfRoomCount == 0 && javaReportLocation != null) {
            context.report(ISSUE, javaReportLocation, "Prefer `SHOW_AS_ACTION_IF_ROOM` over `SHOW_AS_ACTION_ALWAYS`");
        }
    }
}