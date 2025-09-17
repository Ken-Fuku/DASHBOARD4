package com.kenfukuda.dashboard.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "run-cli", mixinStandardHelpOptions = true, version = "0.1",
        description = "Dashboard utility CLI",
        subcommands = {ExportChangeLogCommand.class, ImportChangeLogCommand.class})
public class RunCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RunCli()).execute(args);
        System.exit(exitCode);
    }
}

