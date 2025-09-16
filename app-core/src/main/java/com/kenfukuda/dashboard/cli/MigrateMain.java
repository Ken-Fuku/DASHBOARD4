package com.kenfukuda.dashboard.cli;

import com.kenfukuda.dashboard.infrastructure.db.MigrationRunner;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class MigrateMain {
    private static final Logger logger = LoggerFactory.getLogger(MigrateMain.class);

    public static void main(String[] args) throws Exception {
        String dbPath = "./data/app.db";
        SqliteConnectionManager cm = new SqliteConnectionManager(dbPath);
        // apply pragmas early
        com.kenfukuda.dashboard.infrastructure.db.SqlitePragmaConfigurer cfg = new com.kenfukuda.dashboard.infrastructure.db.SqlitePragmaConfigurer(cm);
        java.util.Map<String,String> pragmas = new java.util.HashMap<>();
        pragmas.put("journal_mode", "WAL");
        pragmas.put("synchronous", "NORMAL");
        pragmas.put("foreign_keys", "ON");
        pragmas.put("temp_store", "MEMORY");
        cfg.apply(pragmas);

        logger.info("Starting migrations from db/migrations to {}", dbPath);
        MigrationRunner runner = new MigrationRunner(cm, Path.of("db/migrations"));
        runner.run();
        logger.info("Migrations complete");
    }
}
