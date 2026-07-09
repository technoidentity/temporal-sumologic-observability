package com.example.demo;

import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

  private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);

  @Value("${temporal.target:localhost:7233}")
  private String temporalTarget;

  @Value("${temporal.namespace:default}")
  private String temporalNamespace;

  @Value("${temporal.task-queue:HELLO_TASK_QUEUE}")
  private String temporalTaskQueue;

  // When set (e.g. for Temporal Cloud), the worker authenticates with this API key over TLS.
  // Leave empty for a local/unsecured dev server.
  @Value("${temporal.api-key:}")
  private String temporalApiKey;

  /**
   * Service stubs wired with a Tally metrics scope backed by the Spring-managed Micrometer
   * registry. Every Temporal SDK / worker metric (e.g. temporal_workflow_completed,
   * temporal_activity_execution_latency, temporal_worker_task_slots_available) is therefore
   * exposed on /actuator/prometheus and scraped by the Sumo Logic OTel collector.
   */
  @Bean
  public WorkflowServiceStubs workflowServiceStubs(MeterRegistry meterRegistry) {
    Scope metricsScope =
        new RootScopeBuilder()
            .reporter(new MicrometerClientStatsReporter(meterRegistry))
            .reportEvery(com.uber.m3.util.Duration.ofSeconds(10));

    WorkflowServiceStubsOptions.Builder builder =
        WorkflowServiceStubsOptions.newBuilder()
            .setTarget(temporalTarget)
            .setMetricsScope(metricsScope);

    if (temporalApiKey != null && !temporalApiKey.isBlank()) {
      // Temporal Cloud (API key auth). TLS is auto-enabled, but we set it explicitly.
      builder.addApiKey(() -> temporalApiKey).setEnableHttps(true);
      log.info(
          "Connecting to Temporal Cloud target={} namespace={} (API key auth)",
          temporalTarget,
          temporalNamespace);
    } else {
      log.info(
          "Connecting to local/unsecured Temporal target={} namespace={}",
          temporalTarget,
          temporalNamespace);
    }

    return WorkflowServiceStubs.newServiceStubs(builder.build());
  }

  @Bean
  public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
    return WorkflowClient.newInstance(
        serviceStubs,
        WorkflowClientOptions.newBuilder().setNamespace(temporalNamespace).build());
  }

  @Bean
  public ScheduleClient scheduleClient(WorkflowServiceStubs serviceStubs) {
    return ScheduleClient.newInstance(
        serviceStubs,
        ScheduleClientOptions.newBuilder().setNamespace(temporalNamespace).build());
  }

  @Bean(destroyMethod = "shutdown")
  public WorkerFactory workerFactory(WorkflowClient workflowClient) {
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(temporalTaskQueue);
    worker.registerWorkflowImplementationTypes(HelloWorkflowImpl.class);
    worker.registerActivitiesImplementations(new HelloActivitiesImpl());
    factory.start();
    log.info("Temporal worker started, polling task queue={}", temporalTaskQueue);
    return factory;
  }
}
