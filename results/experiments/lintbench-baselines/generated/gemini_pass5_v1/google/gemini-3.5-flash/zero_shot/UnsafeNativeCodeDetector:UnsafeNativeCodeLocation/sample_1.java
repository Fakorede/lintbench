com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import org.jetbrains.annotations.NonNull;

public class UnsafeNativeCodeDetector extends Detector implements Detector.OtherFileScanner {

    public static final Issue ISSUE = Issue.create(
        "UnsafeNativeCodeLocation",
        "Native code outside library directory",
        "In general, application native code should only be placed in the application's " +
        "library directory, not in other locations such as the res or assets directories. " +
        "Placing the code in the library directory provides increased assurance that the " +
        "code will not be tampered with after application installation. Application " +
        "developers should use the features of their development environment to place " +
        "application native libraries into the lib directory of their compiled " +
        "APKs. Embedding non-shared library native executables into applications should " +
        "be avoided when possible.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(
            UnsafeNativeCodeDetector.class,
            EnumSet.of(Scope.RESOURCE_FILE, Scope.OTHER)
        )
    );

    @NonNull
    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.RESOURCE_FILE, Scope.OTHER);
    }

    @Override
    public void run(@NonNull Context context) {
        File file = context.file;
        if (isInsideUnsafeLocation(context, file) && isNativeCode(file)) {
            context.report(
                ISSUE,
                Location.create(file),
                "Native code should not be placed in res or assets directories. " +
                "Place it in the jniLibs/lib directory instead."
            );
        }
    }

    private boolean isInsideUnsafeLocation(Context context, File file) {
        String path = file.getPath().replace('\\', '/');
        if (path.contains("/assets/") || path.contains("/res/")) {
            return true;
        }
        Project project = context.getProject();
        for (File dir : project.getAssetDirs()) {
            if (isAncestor(dir, file)) {
                return true;
            }
        }
        for (File dir : project.getResourceDirs()) {
            if (isAncestor(dir, file)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAncestor(File ancestor, File file) {
        File parent = file.getParentFile();
        while (parent != null) {
            if (parent.equals(ancestor)) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }

    private boolean isNativeCode(File file) {
        String name = file.getName();
        if (name.endsWith(".so")) {
            return true;
        }
        if (file.isFile() && file.length() >= 4) {
            try (InputStream is = new FileInputStream(file)) {
                byte[] header = new byte[4];
                int read = is.read(header);
                if (read == 4) {
                    return header[0] == (byte) 0x7F &&
                           header[1] == (byte) 0x45 && // 'E'
                           header[2] == (byte) 0x4C && // 'L'
                           header[3] == (byte) 0x46;   // 'F'
                }
            } catch (IOException e) {
                // Ignore and treat as non-native
            }
        }
        return false;
    }
}