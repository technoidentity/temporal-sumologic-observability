package com.example.demo;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemporalController {

  private static final Logger log = LoggerFactory.getLogger(TemporalController.class);

  private final WorkflowClient workflowClient;

  public TemporalController(WorkflowClient workflowClient) {
    this.workflowClient = workflowClient;
  }

  @GetMapping("/temporal/hello")
  public String runHelloWorkflow(@RequestParam(defaultValue = "Temporal") String name) {
    String workflowId = "hello-" + System.currentTimeMillis();
    log.info("Starting HelloWorkflow workflowId={} name={}", workflowId, name);

    HelloWorkflow workflow =
        workflowClient.newWorkflowStub(
            HelloWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .setWorkflowId(workflowId)
                .build());

    String result = workflow.sayHello(name);
    log.info("Finished HelloWorkflow workflowId={} result={}", workflowId, result);
    return result;
  }
}
