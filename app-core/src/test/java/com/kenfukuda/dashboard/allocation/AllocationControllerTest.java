package com.kenfukuda.dashboard.allocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class AllocationControllerTest {

    @Test
    public void testCompositeApi() {
        AllocationController ctrl = new AllocationController();
        Object res = ctrl.getDaily(2025, 9, 300.0, "A:600|B:300");
        assertNotNull(res);
        // crude structural checks via toString/json mapping not available here; just ensure no exception and type
        assertTrue(res.getClass().getSimpleName().contains("CompositeResponse") || res.getClass().getSimpleName().contains("AllocationResponse"));
    }
}
