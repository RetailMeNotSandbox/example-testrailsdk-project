package com.retailmenotsandbox.exampletestrailsdk;

import com.rmn.testrail.entity.Project;
import com.rmn.testrail.entity.TestInstance;
import com.rmn.testrail.entity.TestPlan;
import com.rmn.testrail.entity.TestResult;
import com.rmn.testrail.entity.TestResults;
import com.rmn.testrail.entity.TestRun;
import com.rmn.testrail.service.TestRailService;
import lombok.extern.slf4j.Slf4j;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class BaseTest {

    private TestRailService testRailService;
    private Map<Integer, Integer> caseToInstanceMap = new HashMap<>();
    private Map<Integer, Integer> caseToRunIdMap = new HashMap<>();
    private Map<Integer, TestResults> runIdToResultsMap = new HashMap<>();

    /**
     * initializes TestRail service
     */
    @BeforeSuite
    public void startTestRailService() {
        testRailService = new TestRailService("clientId", "username", "password");
        String projectName = System.getProperty("testRailProject", "YourProjectName");
        Project project = testRailService.getProjectByName(projectName);
        // exit if the requested project does not exist
        if (project == null) {
            log.error("Requested project '" + projectName + "' does not exist.");
            return;
        }
        String testPlanName = System.getProperty("testRailTestPlan", "YourActiveTestPlanName");
        TestPlan testPlanByName = project.getTestPlanByName(testPlanName);
        // exit if the requested test plan does not exist
        if (testPlanByName == null) {
            log.error("Requested test plan '" + testPlanName + "' does not exist in project '" + projectName + "'");
            return;
        }
        // generate hashmaps linking run ID to associated results and test cases to associated run ID. This allows us to report all results to a run ID in 1 API call later.
        List<TestRun> testRuns = testPlanByName.getTestRuns();
        for (TestRun testRun : testRuns) {
            Integer testRunId = testRun.getId();
            if (!runIdToResultsMap.containsKey(testRunId)) {
                runIdToResultsMap.put(testRunId, new TestResults());
            }
            List<TestInstance> tests = testRun.getTests();
            for (TestInstance test : tests) {
                Integer caseId = test.getCaseId();
                caseToRunIdMap.put(caseId, testRunId);
                caseToInstanceMap.put(caseId, test.getId());
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    public final void teardownTestScope(Method m, ITestResult result) {
        addTestResultToMap(m, result);
    }

    /**
     * sends test results to TestRail
     */
    @AfterSuite(alwaysRun = true)
    public final void reportToTestRail() {
        for (Integer runId : runIdToResultsMap.keySet()) {
            TestResults results = runIdToResultsMap.get(runId);
            if (results.getResults().size() > 0) {
                testRailService.addTestResults(runId, results);
            }
        }
    }

    /**
     * adds test results to a hashmap linking to the appropriate run ID so results can be reported to a run using a single API call
     * @param m
     * @param result
     */
    private void addTestResultToMap(Method m, ITestResult result) {
        if (runIdToResultsMap.size() == 0) {
            return;
        }
        int status = result.getStatus();
        String description = m.getAnnotation(Test.class).description();
        Integer caseId;
        if (description.length() > 0){
            caseId = Integer.valueOf(description);
        } else {
            return;
        }
        Integer testInstanceId = caseToInstanceMap.get(caseId);

        TestResult testResult = new TestResult();
        testResult.setTestId(testInstanceId);
        testResult.setAssignedtoId(1); //user int ID that the test will be marked as assigned to
        switch (status) {
            case ITestResult.FAILURE:
                testResult.setVerdict("Failed");
                break;
            case ITestResult.SUCCESS:
                testResult.setVerdict("Passed");
                break;
            default:
                testResult.setVerdict("Retest");
                break;
        }
        Throwable throwable = result.getThrowable();
        if (throwable != null) {
            testResult.setComment(result.getThrowable().getMessage());
        }
        Integer runId = caseToRunIdMap.get(caseId);
        if (runId == null){
            log.error("Test " + m.getName() + " for test case " + caseId + " is not present in the current test plan");
            return;
        }
        TestResults testResults = runIdToResultsMap.get(runId);
        testResults.addResult(testResult);
        runIdToResultsMap.put(runId, testResults);
    }
}
