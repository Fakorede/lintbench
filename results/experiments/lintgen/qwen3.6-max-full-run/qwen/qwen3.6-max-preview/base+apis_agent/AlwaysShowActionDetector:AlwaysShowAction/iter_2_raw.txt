package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

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
        new Implementation(AlwaysShowActionDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)
    );

    private Map<File, int[]> xmlFileCounts;
    private int javaAlwaysCount;
    private int javaIfRoomCount;
    private List<JavaContext> javaAlwaysContexts;
    private List<UElement> javaAlwaysElements;

    @Override
    public void beforeCheckRootProject(Context context) {
        xmlFileCounts = new HashMap<>();
        javaAlwaysCount = 0;
        javaIfRoomCount = 0;
        javaAlwaysContexts = new ArrayList<>();
        javaAlwaysElements = new ArrayList<>();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        File parent = context.file.getParentFile();
        if (parent == null || !parent.getName().equals(ResourceFolderType.MENU.getName())) {
            return;
        }

        String value = getShowAsActionValue(element);
        if (value == null) return;

        int[] counts = xmlFileCounts.computeIfAbsent(context.file, f -> new int[2]);
        if ("always".equals(value)) {
            counts[0]++;
        } else if ("ifRoom".equals(value)) {
            counts[1]++;
        }
    }

    private String getShowAsActionValue(Element element) {
        String val = element.getAttributeNS(SdkConstants.ANDROID_URI, "showAsAction");
        if (val != null && !val.isEmpty()) return val;
        val = element.getAttributeNS(SdkConstants.AUTO_URI, "showAsAction");
        if (val != null && !val.isEmpty()) return val;
        return null;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof XmlContext) {
            File file = context.file;
            int[] counts = xmlFileCounts.remove(file);
            if (counts != null) {
                int always = counts[0];
                int ifRoom = counts[1];
                if (always > 2 || (always > 0 && ifRoom == 0)) {
                    context.report(ISSUE, Location.create(file),
                        "Prefer `ifRoom` instead of `always` for `showAsAction`");
                }
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(JavaContext context, UReferenceExpression reference, PsiElement referenced) {
        if (!(referenced instanceof PsiField)) return;

        PsiClass containingClass = ((PsiField) referenced).getContainingClass();
        if (containingClass == null || !"android.view.MenuItem".equals(containingClass.getQualifiedName())) {
            return;
        }

        String name = ((PsiField) referenced).getName();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            javaAlwaysCount++;
            javaAlwaysContexts.add(context);
            javaAlwaysElements.add(reference);
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            javaIfRoomCount++;
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (javaAlwaysCount > 0 && javaIfRoomCount == 0) {
            for (int i = 0; i < javaAlwaysContexts.size(); i++) {
                JavaContext ctx = javaAlwaysContexts.get(i);
                UElement elem = javaAlwaysElements.get(i);
                ctx.report(ISSUE, ctx.getLocation(elem),
                    "Prefer `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
            }
        }
        xmlFileCounts = null;
        javaAlwaysContexts = null;
        javaAlwaysElements = null;
    }
}