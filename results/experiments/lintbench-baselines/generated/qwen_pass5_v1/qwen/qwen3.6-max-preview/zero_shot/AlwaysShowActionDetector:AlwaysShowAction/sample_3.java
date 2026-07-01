package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class AlwaysShowActionDetector extends Detector implements ResourceXmlDetector, UastScanner {

    private static final String KEY_ALWAYS_COUNT = "AlwaysShowActionDetector:alwaysCount";
    private static final String KEY_IFROOM_COUNT = "AlwaysShowActionDetector:ifRoomCount";
    private static final String KEY_JAVA_ALWAYS = "AlwaysShowActionDetector:javaAlways";
    private static final String KEY_JAVA_IFROOM = "AlwaysShowActionDetector:javaIfRoom";

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
        new Implementation(AlwaysShowActionDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
    );

    // --- XML Handling ---

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        context.putClientProperty(KEY_ALWAYS_COUNT, 0);
        context.putClientProperty(KEY_IFROOM_COUNT, 0);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String value = getShowAsActionValue(element);
        if (value != null) {
            if (hasFlag(value, "always")) {
                context.putClientProperty(KEY_ALWAYS_COUNT, (Integer) context.getClientProperty(KEY_ALWAYS_COUNT) + 1);
            }
            if (hasFlag(value, "ifRoom")) {
                context.putClientProperty(KEY_IFROOM_COUNT, (Integer) context.getClientProperty(KEY_IFROOM_COUNT) + 1);
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        Integer always = (Integer) context.getClientProperty(KEY_ALWAYS_COUNT);
        Integer ifRoom = (Integer) context.getClientProperty(KEY_IFROOM_COUNT);
        if (always == null) always = 0;
        if (ifRoom == null) ifRoom = 0;

        if (always > 2 || (always > 0 && ifRoom == 0)) {
            context.report(ISSUE, Location.create(context.file),
                "Consider using `ifRoom` instead of `always`");
        }
    }

    @Nullable
    private static String getShowAsActionValue(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if ("showAsAction".equals(attr.getLocalName())) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    private static boolean hasFlag(@NonNull String value, @NonNull String flag) {
        String[] parts = value.split("\\|");
        for (String part : parts) {
            if (part.trim().equals(flag)) {
                return true;
            }
        }
        return false;
    }

    // --- Java/UAST Handling ---

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiField) {
                    String qname = ((PsiField) resolved).getQualifiedName();
                    if ("android.view.MenuItem.SHOW_AS_ACTION_ALWAYS".equals(qname)) {
                        context.getProject().putClientProperty(KEY_JAVA_ALWAYS, true);
                    } else if ("android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM".equals(qname)) {
                        context.getProject().putClientProperty(KEY_JAVA_IFROOM, true);
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        Boolean hasAlways = (Boolean) context.getProject().getClientProperty(KEY_JAVA_ALWAYS);
        Boolean hasIfRoom = (Boolean) context.getProject().getClientProperty(KEY_JAVA_IFROOM);

        if (Boolean.TRUE.equals(hasAlways) && !Boolean.TRUE.equals(hasIfRoom)) {
            context.report(ISSUE, Location.create(context.getProject().getDir()),
                "The project uses `SHOW_AS_ACTION_ALWAYS` but never `SHOW_AS_ACTION_IF_ROOM`. " +
                "Consider using `ifRoom` instead.");
        }
    }
}