package com.robot.platform.ai.digitalhuman;

import com.robot.platform.ai.digitalhuman.controller.admin.AiDigitalHumanAdminController;
import com.robot.platform.ai.digitalhuman.controller.admin.DigitalHumanRenderController;
import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
import com.robot.platform.ai.digitalhuman.provider.DigitalHumanProviders;
import com.robot.platform.ai.digitalhuman.service.AiDigitalHumanService;
import com.robot.platform.ai.digitalhuman.service.DigitalHumanPreviewSessions;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.framework.web.config.RobotPlatformWebAutoConfiguration;
import com.robot.platform.framework.web.config.WebProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the production /admin-api prefix and both controllers to cover static-vs-ID routing. */
class DigitalHumanRenderRoutingTest {
    private AiDigitalHumanService humans;
    private DigitalHumanProviders providers;
    private MockMvc mvc;

    @BeforeEach void setup() {
        humans = mock(AiDigitalHumanService.class);
        providers = mock(DigitalHumanProviders.class);
        var web = new RobotPlatformWebAutoConfiguration().webMvcRegistrations(new WebProperties());
        mvc = MockMvcBuilders.standaloneSetup(new AiDigitalHumanAdminController(humans),
                        new DigitalHumanRenderController(humans, providers, mock(DigitalHumanPreviewSessions.class)))
                .setCustomHandlerMapping(web::getRequestMappingHandlerMapping).build();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void renderServicesUsesItsStaticRouteAndReturnsEmptyListWhenNoServiceIsEnabled() throws Exception {
        when(providers.services()).thenReturn(List.of());
        mvc.perform(get("/admin-api/ai/digital-humans/render-services"))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(DigitalHumanRenderController.class))
                .andExpect(handler().methodName("services"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(humans);
    }

    @Test void numericIdStillUsesTheExistingDetailRoute() throws Exception {
        var human = new AiDigitalHumanDO();
        human.setId(42L);
        when(humans.get(1L, 42L)).thenReturn(human);
        mvc.perform(get("/admin-api/ai/digital-humans/42"))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(AiDigitalHumanAdminController.class))
                .andExpect(jsonPath("$.data.id").value(42));
        verifyNoInteractions(providers);
    }
}
