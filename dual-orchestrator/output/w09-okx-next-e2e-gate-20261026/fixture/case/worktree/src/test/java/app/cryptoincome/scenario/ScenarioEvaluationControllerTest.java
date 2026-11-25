package app.cryptoincome.scenario;

import app.cryptoincome.scenario.application.ScenarioEvaluationCommand;
import app.cryptoincome.scenario.application.ScenarioEvaluationService;
import app.cryptoincome.scenario.domain.ScenarioEvaluationException;
import app.cryptoincome.scenario.domain.ScenarioEvaluationFailureCode;
import app.cryptoincome.web.ScenarioEvaluationController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static app.cryptoincome.scenario.ScenarioEvaluationFixtures.command;
import static app.cryptoincome.scenario.ScenarioEvaluationServiceTest.service;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScenarioEvaluationControllerTest {
    private final JsonMapper mapper = new JsonMapper();

    @Test
    void returnsUnifiedReadModelWithoutBrowserSideCalculations() throws Exception {
        ScenarioEvaluationCommand request = command();

        mvc(service()).perform(post("/api/scenario-evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.evaluationId").value("eval-batch-b"))
                .andExpect(jsonPath("$.optionSet.options").isArray())
                .andExpect(jsonPath("$.optionSet.options.length()").value(
                        org.hamcrest.Matchers.both(org.hamcrest.Matchers.greaterThanOrEqualTo(1))
                                .and(org.hamcrest.Matchers.lessThanOrEqualTo(3))))
                .andExpect(jsonPath("$.optionSet.options[0].allocations[0].ratio").isNumber())
                .andExpect(jsonPath("$.optionSet.options[0].pathOutcomes[0].netIncomeUsdt").isNumber());
    }

    @Test
    void mapsInvalidNoFeasibleAndInvariantFailuresToStableCodes() throws Exception {
        ScenarioEvaluationService mocked = mock(ScenarioEvaluationService.class);
        MockMvc mvc = mvc(mocked);
        mvc.perform(post("/api/scenario-evaluations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCENARIO_REQUEST"));

        doThrow(new ScenarioEvaluationException(
                ScenarioEvaluationFailureCode.NO_FEASIBLE_PORTFOLIO, "没有可行组合"))
                .when(mocked).evaluate(any());
        mvc.perform(post("/api/scenario-evaluations").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(command())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_FEASIBLE_PORTFOLIO"));

        doThrow(new IllegalStateException("不应暴露的内部细节")).when(mocked).evaluate(any());
        mvc.perform(post("/api/scenario-evaluations").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(command())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SCENARIO_CALCULATION_FAILED"))
                .andExpect(jsonPath("$.message").value("情景计算不变量失败"));
    }

    private MockMvc mvc(ScenarioEvaluationService service) {
        return MockMvcBuilders.standaloneSetup(new ScenarioEvaluationController(service))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper)).build();
    }
}
