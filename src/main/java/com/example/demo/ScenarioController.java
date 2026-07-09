package com.example.demo;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.client.schedules.ScheduleState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScenarioController {

  private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);
  private static final String WORKFLOW_TYPE = "HelloWorkflow";

  private final WorkflowClient workflowClient;
  private final ScheduleClient scheduleClient;
  private final String temporalTaskQueue;

  public ScenarioController(
      WorkflowClient workflowClient,
      ScheduleClient scheduleClient,
      @Value("${temporal.task-queue:HELLO_TASK_QUEUE}") String temporalTaskQueue) {
    this.workflowClient = workflowClient;
    this.scheduleClient = scheduleClient;
    this.temporalTaskQueue = temporalTaskQueue;
  }

  @PostMapping("/temporal/scenarios/{scenario}")
  public ScenarioResponse startScenario(
      @PathVariable String scenario,
      @RequestParam(defaultValue = "1") int count,
      @RequestParam(defaultValue = "DASHBOARD_NO_WORKER_TQ") String noWorkerTaskQueue) {
    String normalized = scenario.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "success" -> startMany("success", "DashboardScenario", temporalTaskQueue, count, null);
      case "workflow-fail" ->
          startMany("workflow-fail", "scenario:workflow-fail", temporalTaskQueue, count, null);
      case "activity-fail" ->
          startMany("activity-fail", "scenario:activity-fail", temporalTaskQueue, count, null);
      case "timeout" ->
          startMany(
              "timeout",
              "scenario:sleep:30",
              temporalTaskQueue,
              count,
              Duration.ofSeconds(5));
      case "task-fail" ->
          startMany(
              "task-fail",
              "scenario:task-fail",
              temporalTaskQueue,
              count,
              Duration.ofSeconds(30));
      case "continue-as-new" ->
          startMany("continue-as-new", "scenario:continue:2", temporalTaskQueue, count, null);
      case "cancel" -> startAndCancel();
      case "terminate" -> startAndTerminate();
      case "backlog" ->
          startMany(
              "backlog",
              "DashboardBacklog",
              noWorkerTaskQueue,
              count,
              Duration.ofSeconds(90));
      default ->
          throw new IllegalArgumentException(
              "Unsupported scenario. Use success, workflow-fail, activity-fail, timeout, task-fail,"
                  + " continue-as-new, cancel, terminate, or backlog.");
    };
  }

  @PostMapping("/temporal/scenarios/burst")
  public ScenarioResponse startBurst(@RequestParam(defaultValue = "20") int count) {
    return startMany("burst", "DashboardBurst", temporalTaskQueue, count, null);
  }

  @PostMapping("/temporal/scenarios/schedule")
  public ScheduleResponse createSchedule(
      @RequestParam(defaultValue = "2") long remainingActions,
      @RequestParam(defaultValue = "30") long intervalSeconds) {
    String scheduleId = "dashboard-schedule-" + System.currentTimeMillis();
    WorkflowOptions workflowOptions =
        WorkflowOptions.newBuilder()
            .setWorkflowId(scheduleId + "-workflow")
            .setTaskQueue(temporalTaskQueue)
            .build();

    Schedule schedule =
        Schedule.newBuilder()
            .setAction(
                ScheduleActionStartWorkflow.newBuilder()
                    .setWorkflowType(WORKFLOW_TYPE)
                    .setOptions(workflowOptions)
                    .setArguments("DashboardSchedule")
                    .build())
            .setSpec(
                ScheduleSpec.newBuilder()
                    .setIntervals(List.of(new ScheduleIntervalSpec(Duration.ofSeconds(intervalSeconds))))
                    .build())
            .setPolicy(
                SchedulePolicy.newBuilder()
                    .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                    .build())
            .setState(
                ScheduleState.newBuilder()
                    .setLimitedAction(true)
                    .setRemainingActions(remainingActions)
                    .setNote("Short-lived schedule for Sumo dashboard validation")
                    .build())
            .build();

    ScheduleHandle handle =
        scheduleClient.createSchedule(scheduleId, schedule, ScheduleOptions.newBuilder().build());
    log.info(
        "Created dashboard validation schedule scheduleId={} remainingActions={} intervalSeconds={}",
        scheduleId,
        remainingActions,
        intervalSeconds);

    return new ScheduleResponse(
        "schedule",
        scheduleId,
        handle.getId(),
        "Schedule will stop after remainingActions is exhausted. Delete it if validation ends early.");
  }

  @DeleteMapping("/temporal/scenarios/schedule/{scheduleId}")
  public ScheduleResponse deleteSchedule(@PathVariable String scheduleId) {
    scheduleClient.getHandle(scheduleId).delete();
    log.info("Deleted dashboard validation schedule scheduleId={}", scheduleId);
    return new ScheduleResponse("schedule-delete", scheduleId, scheduleId, "Deleted");
  }

  private ScenarioResponse startAndCancel() {
    WorkflowRun run =
        startWorkflow(
            "cancel", "scenario:sleep:60", temporalTaskQueue, Duration.ofSeconds(120));
    workflowClient.newUntypedWorkflowStub(run.workflowId()).cancel("Dashboard validation cancel");
    return new ScenarioResponse("cancel", List.of(run), "Cancel requested");
  }

  private ScenarioResponse startAndTerminate() {
    WorkflowRun run =
        startWorkflow(
            "terminate", "scenario:sleep:60", temporalTaskQueue, Duration.ofSeconds(120));
    workflowClient
        .newUntypedWorkflowStub(run.workflowId())
        .terminate("Dashboard validation terminate");
    return new ScenarioResponse("terminate", List.of(run), "Terminate requested");
  }

  private ScenarioResponse startMany(
      String scenario, String input, String taskQueue, int count, Duration executionTimeout) {
    int boundedCount = Math.max(1, Math.min(count, 200));
    List<WorkflowRun> runs = new ArrayList<>(boundedCount);
    for (int i = 0; i < boundedCount; i++) {
      runs.add(startWorkflow(scenario, input, taskQueue, executionTimeout));
    }
    return new ScenarioResponse(scenario, runs, "Started " + boundedCount + " workflow(s)");
  }

  private WorkflowRun startWorkflow(
      String scenario, String input, String taskQueue, Duration executionTimeout) {
    String workflowId =
        "dashboard-"
            + scenario
            + "-"
            + System.currentTimeMillis()
            + "-"
            + UUID.randomUUID().toString().substring(0, 8);
    WorkflowOptions.Builder options =
        WorkflowOptions.newBuilder().setTaskQueue(taskQueue).setWorkflowId(workflowId);
    if (executionTimeout != null) {
      options.setWorkflowExecutionTimeout(executionTimeout);
    }

    WorkflowStub workflow = workflowClient.newUntypedWorkflowStub(WORKFLOW_TYPE, options.build());
    WorkflowExecution execution = workflow.start(input);
    log.info(
        "Started dashboard scenario workflow scenario={} workflowId={} runId={} taskQueue={}",
        scenario,
        workflowId,
        execution.getRunId(),
        taskQueue);
    return new WorkflowRun(scenario, workflowId, execution.getRunId(), taskQueue);
  }

  public record ScenarioResponse(String scenario, List<WorkflowRun> workflows, String note) {}

  public record ScheduleResponse(
      String scenario, String scheduleId, String handleId, String note) {}

  public record WorkflowRun(String scenario, String workflowId, String runId, String taskQueue) {}
}
