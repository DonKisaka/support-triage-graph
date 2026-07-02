package com.example.supporttriagegraph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.supporttriagegraph.graph.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class SupportGraphConfiguration {

  static final String CLASSIFY = "classify";
  static final String HUMAN_CLASSIFY = "human-classify";
  static final String BILLING = "billing";
  static final String TECHNICAL = "technical";
  static final String CHECK_RESOLUTION = "check-resolution";
  static final String ESCALATE_TO_HUMAN = "escalate-to-human";

  @Bean
  CompiledGraph supportGraph(
      ClassifySupportRequestNode classifyNode,
      HumanClassificationNode humanClassifyNode,
      BillingSupportNode billingNode,
      TechnicalSupportNode technicalNode,
      CheckResolutionNode checkResolutionNode,
      EscalateToHumanNode escalateNode) throws GraphStateException {

    return new StateGraph("support-triage", this::stateStrategies)
        .addNode(CLASSIFY, node_async(classifyNode))
        .addNode(HUMAN_CLASSIFY, node_async(humanClassifyNode))
        .addNode(BILLING, node_async(billingNode))
        .addNode(TECHNICAL, node_async(technicalNode))
        .addNode(CHECK_RESOLUTION, node_async(checkResolutionNode))
        .addNode(ESCALATE_TO_HUMAN, node_async(escalateNode))

        .addEdge(START, CLASSIFY)

        .addConditionalEdges(
            CLASSIFY,
            edge_async(state -> {
              String category = state.value("category", "unknown");
              return switch (category) {
                case BILLING -> BILLING;
                case TECHNICAL -> TECHNICAL;
                default -> HUMAN_CLASSIFY;
              };
            }),
            Map.of(
                BILLING, BILLING,
                TECHNICAL, TECHNICAL,
                HUMAN_CLASSIFY, HUMAN_CLASSIFY
            ))

        .addConditionalEdges(
            HUMAN_CLASSIFY,
            edge_async(state -> state.value("category", TECHNICAL)),
            Map.of(
                BILLING, BILLING,
                TECHNICAL, TECHNICAL
            ))

        .addEdge(BILLING, CHECK_RESOLUTION)
        .addEdge(TECHNICAL, CHECK_RESOLUTION)
        .addEdge(ESCALATE_TO_HUMAN, END)

        .addConditionalEdges(
            CHECK_RESOLUTION,
            edge_async(state -> {
              boolean resolved = state.value("resolved", false);
              int attempts = state.value("attempts", 0);
              String category = state.value("category", TECHNICAL);

              if (resolved) {
                return END;
              }
              if (attempts >= 3) {
                return ESCALATE_TO_HUMAN;
              }
              return category;
            }),
            Map.of(
                END, END,
                BILLING, BILLING,
                TECHNICAL, TECHNICAL,
                ESCALATE_TO_HUMAN, ESCALATE_TO_HUMAN
            ))

        .compile(CompileConfig.builder()
            .interruptBefore(HUMAN_CLASSIFY)
            .build());
  }

  private Map<String, KeyStrategy> stateStrategies() {
    return Map.of(
        "user_question", new ReplaceStrategy(),
        "category", new ReplaceStrategy(),
        "response", new ReplaceStrategy(),
        "feedback", new ReplaceStrategy(),
        "attempts", new ReplaceStrategy(),
        "resolved", new ReplaceStrategy(),
        "escalated", new ReplaceStrategy(),
        "finalResponse", new ReplaceStrategy()
    );
  }

}
