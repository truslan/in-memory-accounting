package me.torobev.billing.cli;

import com.beust.jcommander.Parameter;
import me.torobev.billing.web.WebServer;

public class RunServer implements Runnable {

	@Parameter(names = {"-p", "--port"}, required = true, description = "Server port")
	private int port = 8080;


	@Override
	public void run() {
		try {
			WebServer server = new WebServer(port);
			server.start();
			server.join();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
