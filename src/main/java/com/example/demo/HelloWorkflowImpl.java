package com.example.demo;

import io.temporal.activity.ActivityOptions;
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
    String greeting = activities.composeGreeting(name);
    log.info("HelloWorkflow completed with result={}", greeting);
    return greeting;
  }
}
