package com.retailmenotsandbox.exampletestrailsdk;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ExampleTest extends BaseTest {

    // replace description with your TestRail case ID
    @Test(description="373199")
    public void example(){
        Assert.assertFalse(false);
    }
}
