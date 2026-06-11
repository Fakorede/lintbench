package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.regex.Pattern;

public class ResourcePrefixDetector extends Detector implements Detector.UastScanner {
    private static final String ISSUE_ID = "WrongResourcePrefix";
    private static final String SHORT_DESCRIPTION = "Resource with wrong prefix";
    private static final String EXPLANATION =
            "In Gradle projects, you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries since they all end up in the same shared app namespace.";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            SHORT_DESCRIPTION,
            EXPLANATION,
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private String expectedPrefix;

    @Override
    public void beforeCheck(@NonNull FileContext context) {
        super.beforeCheck(context);
        // Assuming the prefix is defined in a global configuration or passed as an option.
        this.expectedPrefix = "app_";  // Example default value, should be configurable.
    }

    @Override
    public List<Pair<String, String>> getApplicableResourceTypes() {
        return Arrays.asList(
                Pair.create(ResourceFolderType.DRAWABLE.getFolderName(), "*"),
                Pair.create(ResourceFolderType.LAYOUT.getFolderName(), "*"),
                Pair.create(ResourceFolderType.MENU.getFolderName(), "*"),
                Pair.create(ResourceFolderType.VALUE.getFolderName(), "*")
        );
    }

    @Override
    public void visitResource(@NonNull ResourceFile resourceFile, @NonNull Context context) {
        String resourceName = resourceFile.getName();
        if (!resourceName.startsWith(expectedPrefix)) {
            // Report the issue.
            context.report(ISSUE, resourceFile.getVirtualFile(), context.getLocation(resourceFile),
                    "Resource name '" + resourceName + "' does not conform to expected prefix: " + expectedPrefix);
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType type) {
        return true;
    }

    @Override
    public AbstractUastVisitor createUastScanner(@NonNull Context context) {
        return new AbstractUastVisitor() {
            @Override
            public boolean visitCallExpression(UCallExpression node) {
                PsiElement element = node.getJavaPsi();
                if (element != null && element.getParent() instanceof PsiFile) {
                    PsiDirectory directory = ((PsiFile) element.getParent()).getContainingDirectory();
                    while (directory != null) {
                        String dirName = directory.getName();
                        if (dirName.equals(SdkConstants.FD_RES)) {
                            return super.visitCallExpression(node);
                        }
                        directory = directory.getParent();
                    }
                }
                return super.visitCallExpression(node);
            }
        };
    }
}