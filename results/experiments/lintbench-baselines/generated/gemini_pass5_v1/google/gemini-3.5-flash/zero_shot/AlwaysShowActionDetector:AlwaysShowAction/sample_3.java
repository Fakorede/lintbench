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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
            "Java code is usually a deviation from the user interface style guide. Use `ifRoom` " +
            "or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
            "If `always` is used sparingly there are usually no problems and behavior is " +
            "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
            "items. Using it more than twice in the same menu is a bad idea.\n\n" +
            "This check looks for menu XML files that contain more than two `always` " +
            "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
            "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
            "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private final List<Location> alwaysLocations = new ArrayList<>();
    private int alwaysCount = 0;
    private int ifRoomCount = 0;

    private final List<Location> alwaysJavaLocations = new ArrayList<>();
    private int ifRoomJavaCount = 0;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        alwaysJavaLocations.clear();
        ifRoomJavaCount = 0;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (ifRoomJavaCount == 0 && !alwaysJavaLocations.isEmpty()) {
            for (Location location : alwaysJavaLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Prefer \"SHOW_AS_ACTION_IF_ROOM\" instead of \"SHOW_AS_ACTION_ALWAYS\" when no \"SHOW_AS_ACTION_IF_ROOM\" actions are declared"
                );
            }
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context.getResourceFolderType() == ResourceFolderType.MENU) {
            alwaysLocations.clear();
            alwaysCount = 0;
            ifRoomCount = 0;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context.getResourceFolderType() == ResourceFolderType.MENU) {
            if (alwaysCount > 2) {
                for (Location location : alwaysLocations) {
                    context.report(
                            ISSUE,
                            location,
                            "Prefer \"ifRoom\" instead of \"always\" (more than two \"always\" actions in this menu)"
                    );
                }
            } else if (alwaysCount > 0 && ifRoomCount == 0) {
                for (Location location : alwaysLocations) {
                    context.report(
                            ISSUE,
                            location,
                            "Prefer \"ifRoom\" instead of \"always\" when no \"ifRoom\" actions are declared"
                    );
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }

        Attr attribute = null;
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node node = attributes.item(i);
            if (node instanceof Attr) {
                Attr attr = (Attr) node;
                if ("showAsAction".equals(attr.getLocalName())) {
                    attribute = attr;
                    break;
                }
            }
        }

        if (attribute != null) {
            String value = attribute.getValue();
            if (value.contains("always")) {
                alwaysCount++;
                alwaysLocations.add(context.getValueLocation(attribute));
            }
            if (value.contains("ifRoom")) {
                ifRoomCount++;
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
            @NonNull UReferenceExpression referenceExpression,
            @NonNull PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    alwaysJavaLocations.add(context.getNameLocation(referenceExpression));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    ifRoomJavaCount++;
                }
            }
        }
    }
}