package com.example.demo;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemporalController {

  private static final Logger log = LoggerFactory.getLogger(TemporalController.class);

  private final WorkflowClient workflowClient;
  private final String temporalTaskQueue;

  public TemporalController(
      WorkflowClient workflowClient,
      @Value("${temporal.task-queue:HELLO_TASK_QUEUE}") String temporalTaskQueue) {
    this.workflowClient = workflowClient;
    this.temporalTaskQueue = temporalTaskQueue;
  }

  @GetMapping("/temporal/hello")
  public String runHelloWorkflow(@RequestParam(defaultValue = "Temporal") String name) {
    String workflowId = "hello-" + System.currentTimeMillis();
    log.info("Starting HelloWorkflow workflowId={} name={}", workflowId, name);

    HelloWorkflow workflow =
        workflowClient.newWorkflowStub(
            HelloWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(temporalTaskQueue)
                .setWorkflowId(workflowId)
                .build());

    String result = workflow.sayHello(name);
    log.info("Finished HelloWorkflow workflowId={} result={}", workflowId, result);
    return result;
  }
}
