package me.torobev.billing.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import java.util.HashMap;
import java.util.Map;

import static java.lang.System.err;

public class App {

	public static class CommandLineDispatcher {

		@Parameter(names = {"-h", "--help"}, description = "Show this help")
		private boolean help = false;
		private final JCommander jCommander;
		private final Map<String, Runnable> commandActions;

		CommandLineDispatcher(Map<String, Runnable> commandActions) {
			jCommander = new JCommander(this);
			this.commandActions = commandActions;
			for (Map.Entry<String, Runnable> row : commandActions.entrySet()) {
				jCommander.addCommand(row.getKey(), row.getValue());
			}
		}

		static CommandLineDispatcher createDispatcher(Map<String, Runnable> commandActions) {
			return new CommandLineDispatcher(commandActions);
		}

		int performCommand(String[] args) {
			try {
				jCommander.parse(args);
				String commandName = jCommander.getParsedCommand();
				if (help || commandName == null || commandName.isEmpty()) {
					usage(jCommander);
					return 0;
				} else {
					commandActions.get(commandName).run();
					return 0;
				}
			} catch (ParameterException e) {
				err.println(e.getMessage());
				usage(jCommander);
				return -1;
			}
		}

		private static void usage(JCommander commander) {
			StringBuilder builder = new StringBuilder();
			commander.usage(builder);
			err.print(builder.toString());
		}
	}

	public static void main(String[] args) {
		HashMap<String, Runnable> actions = new HashMap<>();
		actions.put("server", new RunServer());
		actions.put("demo", new RunDemo());
		CommandLineDispatcher dispatcher = CommandLineDispatcher.createDispatcher(actions);
		System.exit(dispatcher.performCommand(args));
	}
}
