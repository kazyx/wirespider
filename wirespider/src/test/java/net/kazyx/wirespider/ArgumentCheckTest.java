package net.kazyx.wirespider;

import net.kazyx.wirespider.util.ArgumentCheck;
import org.junit.Test;

public class ArgumentCheckTest {
    @Test(expected = NullPointerException.class)
    public void nullIsRejected() {
        ArgumentCheck.rejectNull(null);
    }

    @Test(expected = NullPointerException.class)
    public void nullInVarArgsIsRejected() {
        ArgumentCheck.rejectNullArgs("notnull", null, "notnull");
    }

    @Test
    public void nullInArrayIsAcceptedByVarArgsCall() {
        String[] arr = {"notnull", null, "notnull"};
        ArgumentCheck.rejectNullArgs((Object) arr);
    }

    @Test(expected = NullPointerException.class)
    public void nullInArrayIsRejectedByNonVarArgsCall() {
        String[] arr = {"notnull", null, "notnull"};
        ArgumentCheck.rejectNullArgs((Object[]) arr);
    }

    @Test
    public void nullInArrayArgIsAccepted() {
        String[] arr = {"notnull", null, "notnull"};
        ArgumentCheck.rejectNull(arr);
    }
}
