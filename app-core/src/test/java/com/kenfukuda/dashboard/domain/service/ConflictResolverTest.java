package com.kenfukuda.dashboard.domain.service;

import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConflictResolverTest {
    @Test
    public void testLwwWins() {
        ConflictResolver r = new ConflictResolver();
        ChangeLogEntry older = new ChangeLogEntry(1L, "tx1", "visit_daily", "{\"id\":1}", "U", "{\"visitors\":10}", 0, "2025-01-01T00:00:00Z");
        ChangeLogEntry newer = new ChangeLogEntry(2L, "tx2", "visit_daily", "{\"id\":1}", "U", "{\"visitors\":20}", 0, "2025-01-02T00:00:00Z");
        ChangeLogEntry win = r.resolve(older, newer, false);
        assertEquals(newer, win);
    }

    @Test
    public void testMasterPrefer() {
        ConflictResolver r = new ConflictResolver();
        ChangeLogEntry main = new ChangeLogEntry(1L, "tx1", "company", "{\"id\":1}", "U", "{\"name\":\"Main\"}", 0, "2025-01-02T00:00:00Z");
        ChangeLogEntry incoming = new ChangeLogEntry(2L, "tx2", "company", "{\"id\":1}", "U", "{\"name\":\"Client\"}", 0, "2025-01-03T00:00:00Z");
        // incomingFromMain=false (incoming is client), existing is main -> prefer existing
        ChangeLogEntry win = r.resolve(main, incoming, false);
        assertEquals(main, win);
        // if incoming is from main, it should win
        ChangeLogEntry win2 = r.resolve(main, incoming, true);
        assertEquals(incoming, win2);
    }
}
