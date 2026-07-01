package test.pkg;
import android.os.Build;
import androidx.credentials.CredentialManager;
import androidx.credentials.CreatePublicKeyCredentialRequest;
public class Test {
    void test(CredentialManager cm, CreatePublicKeyCredentialRequest req) {
        cm.createCredential(null, req, null, null);
    }
}