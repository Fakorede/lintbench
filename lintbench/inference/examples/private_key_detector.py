SOURCE = '''
// EXAMPLE: PrivateKeyDetector (Java, OtherFileScanner)
// Issue: PackagedPrivateKey
// Explanation: In general, you should not package private key files inside your app.

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import kotlin.text.Charsets;

public class PrivateKeyDetector extends Detector implements OtherFileScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "PackagedPrivateKey",
                    "Packaged private key",
                    "In general, you should not package private key files inside your app.",
                    Category.SECURITY,
                    8,
                    Severity.FATAL,
                    new Implementation(PrivateKeyDetector.class, Scope.OTHER_SCOPE))
                    .setAndroidSpecific(true);

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.OTHER_SCOPE;
    }

    @Override
    public void run(Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }
        File file = context.file;
        if (isPrivateKeyFile(file)) {
            String fileName = Lint.getFileNameWithParent(context.getClient(), file);
            context.report(ISSUE, Location.create(file),
                    String.format("The `%1$s` file seems to be a private key file. " +
                            "Please make sure not to embed this in your APK file.", fileName));
        }
    }

    private static boolean isPrivateKeyFile(File file) {
        if (!file.isFile()
                || (!Lint.endsWith(file.getPath(), "pem")
                        && !Lint.endsWith(file.getPath(), "key"))) {
            return false;
        }
        try {
            String firstLine = Files.asCharSource(file, Charsets.US_ASCII).readFirstLine();
            return firstLine != null
                    && firstLine.startsWith("---")
                    && firstLine.contains("PRIVATE KEY");
        } catch (IOException ex) {
            return false;
        }
    }
}
'''.strip()
