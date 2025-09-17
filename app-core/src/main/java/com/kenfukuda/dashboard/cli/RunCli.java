package com.kenfukuda.dashboard.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "run-cli", mixinStandardHelpOptions = true, subcommands = {ExportChangeLogCommand.class, ImportChangeLogCommand.class})
public class RunCli implements Runnable {
    public void run() {
        System.out.println("run-cli: use -h for help or a subcommand");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RunCli()).execute(args);
        System.exit(exitCode);
    }
}
