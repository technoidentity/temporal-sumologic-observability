package com.example.demo;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;

public class HelloWorkflowImpl implements HelloWorkflow {

  private static final Logger log = Workflow.getLogger(HelloWorkflowImpl.class);

  private final HelloActivities activities =
      Workflow.newActivityStub(
          HelloActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .build());

  @Override
  public String sayHello(String name) {
    log.info("HelloWorkflow started for name={}", name);
    if ("scenario:workflow-fail".equals(name)) {
      throw ApplicationFailure.newNonRetryableFailure(
          "Intentional workflow failure for dashboard validation", "DashboardWorkflowFailure");
    }
    if ("scenario:task-fail".equals(name)) {
      throw new IllegalStateException("Intentional workflow task failure for dashboard validation");
    }
    if (name.startsWith("scenario:sleep:")) {
      Workflow.sleep(Duration.ofSeconds(parsePositiveInt(name, "scenario:sleep:", 30)));
      return "Slept for dashboard validation";
    }
    if (name.startsWith("scenario:continue:")) {
      int remaining = parsePositiveInt(name, "scenario:continue:", 0);
      if (remaining > 0) {
        Workflow.continueAsNew("scenario:continue:" + (remaining - 1));
      }
      return "Continue-as-new scenario completed";
    }

    String greeting = activities.composeGreeting(name);
    log.info("HelloWorkflow completed with result={}", greeting);
    return greeting;
  }

  private int parsePositiveInt(String value, String prefix, int fallback) {
    try {
      return Math.max(0, Integer.parseInt(value.substring(prefix.length())));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
