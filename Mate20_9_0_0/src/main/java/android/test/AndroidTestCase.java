package android.test;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import junit.framework.Assert;
import junit.framework.TestCase;

@Deprecated
public class AndroidTestCase extends TestCase {
    protected Context mContext;
    private Context mTestContext;

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Suppress
    public void testAndroidTestCaseSetupProperly() {
        Assert.assertNotNull("Context is null. setContext should be called before tests are run", this.mContext);
    }

    public void setContext(Context context) {
        this.mContext = context;
    }

    public Context getContext() {
        return this.mContext;
    }

    public void setTestContext(Context context) {
        this.mTestContext = context;
    }

    public Context getTestContext() {
        return this.mTestContext;
    }

    public void assertActivityRequiresPermission(String packageName, String className, String permission) {
        Intent intent = new Intent();
        intent.setClassName(packageName, className);
        intent.addFlags(268435456);
        try {
            getContext().startActivity(intent);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("expected security exception for ");
            stringBuilder.append(permission);
            Assert.fail(stringBuilder.toString());
        } catch (SecurityException expected) {
            Assert.assertNotNull("security exception's error message.", expected.getMessage());
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("error message should contain ");
            stringBuilder2.append(permission);
            stringBuilder2.append(".");
            Assert.assertTrue(stringBuilder2.toString(), expected.getMessage().contains(permission));
        }
    }

    public void assertReadingContentUriRequiresPermission(Uri uri, String permission) {
        try {
            getContext().getContentResolver().query(uri, null, null, null, null);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("expected SecurityException requiring ");
            stringBuilder.append(permission);
            Assert.fail(stringBuilder.toString());
        } catch (SecurityException expected) {
            Assert.assertNotNull("security exception's error message.", expected.getMessage());
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("error message should contain ");
            stringBuilder2.append(permission);
            stringBuilder2.append(".");
            Assert.assertTrue(stringBuilder2.toString(), expected.getMessage().contains(permission));
        }
    }

    public void assertWritingContentUriRequiresPermission(Uri uri, String permission) {
        try {
            getContext().getContentResolver().insert(uri, new ContentValues());
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("expected SecurityException requiring ");
            stringBuilder.append(permission);
            Assert.fail(stringBuilder.toString());
        } catch (SecurityException expected) {
            Assert.assertNotNull("security exception's error message.", expected.getMessage());
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("error message should contain \"");
            stringBuilder2.append(permission);
            stringBuilder2.append("\". Got: \"");
            stringBuilder2.append(expected.getMessage());
            stringBuilder2.append("\".");
            Assert.assertTrue(stringBuilder2.toString(), expected.getMessage().contains(permission));
        }
    }

    protected void scrubClass(Class<?> cls) throws IllegalAccessException {
        for (Field field : getClass().getDeclaredFields()) {
            if (!(field.getType().isPrimitive() || Modifier.isStatic(field.getModifiers()))) {
                try {
                    field.setAccessible(true);
                    field.set(this, null);
                } catch (Exception e) {
                    Log.d("TestCase", "Error: Could not nullify field!");
                }
                if (field.get(this) != null) {
                    Log.d("TestCase", "Error: Could not nullify field!");
                }
            }
        }
    }
}
